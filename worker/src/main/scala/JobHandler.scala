/**
 * *
 * JobHandler.scala
 *
 * Overview Project,June 2012
 * @author Jonas Karlsson
 */

import com.jolbox.bonecp._
import org.overviewproject.database.{ DatabaseConfiguration, DataSource, DB }
import java.sql.Connection
import overview.util.{ DocumentProducerFactory, ExceptionStatusMessage, JobRestarter, Logger }
import overview.util.Progress._
import persistence._
import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import org.overviewproject.clustering.DocumentSetIndexer
import org.overviewproject.database.Database
import org.overviewproject.http.{ AsyncHttpRequest, DocumentCloudDocumentProducer }

object JobHandler {

  val asyncHttpRetriever: AsyncHttpRequest = new AsyncHttpRequest
  // Run a single job
  def handleSingleJob(j: PersistentDocumentSetCreationJob): Unit = {
    try {
      Logger.info("Handling job")
      j.state = InProgress
      Database.inTransaction { j.update }
      j.observeCancellation(deleteCancelledJob)

      val documentSetId = j.documentSetId

      val documentWriter = new DocumentWriter(documentSetId)
      val nodeWriter = new NodeWriter(documentSetId)
      def progFn(prog: Progress) = {
        j.fractionComplete = prog.fraction
        j.statusDescription = Some(prog.status.toString)
        Database.inTransaction { j.update }
        val cancelJob = j.state == Cancelled
        val logLabel = if (cancelJob) "CANCELLED"
        else "PROGRESS"

        Logger.info(logLabel + ": " + prog.fraction * 100 + "% done. " + prog.status + ", " + (if (prog.hasError) "ERROR" else "OK"))
        cancelJob
      }

      val documentSet = DB.withConnection { implicit connection =>
        DocumentSetLoader.load(j.documentSetId).get
      }

      val indexer = new DocumentSetIndexer(nodeWriter, documentWriter, progFn)
      val producer = DocumentProducerFactory.create(j, documentSet, indexer, progFn, asyncHttpRetriever)

      producer.produce()

      Database.inTransaction { j.delete }

    } catch {
      case t: Throwable =>
        Logger.error("Job failed: " + t.toString + "\n" + t.getStackTrace.mkString("\n"))
        j.state = Error
        j.statusDescription = Some(ExceptionStatusMessage(t))
        Database.inTransaction {
          j.update
          if (j.state == Cancelled) j.delete
        }
    }
  }

  // Run each job currently listed in the database
  def scanForJobs: Unit = {

    val submittedJobs: Seq[PersistentDocumentSetCreationJob] = Database.inTransaction {
      PersistentDocumentSetCreationJob.findJobsWithState(NotStarted)
    }

    for (j <- submittedJobs) {
      handleSingleJob(j)
      System.gc()
    }
  }

  def restartInterruptedJobs(implicit c: Connection) {
    Database.inTransaction {
      val interruptedJobs = PersistentDocumentSetCreationJob.findJobsWithState(InProgress)
      val restarter = new JobRestarter(new DocumentSetCleaner)

      restarter.restart(interruptedJobs)
    }
  }

  def main(args: Array[String]) {

    val pollingInterval = 500 //milliseconds

    val config = new DatabaseConfiguration()
    val dataSource = new DataSource(config)

    DB.connect(dataSource)

    DB.withConnection { implicit connection =>
      restartInterruptedJobs
    }

    while (true) {
      scanForJobs
      Thread.sleep(pollingInterval)
    }
  }

  def deleteCancelledJob(job: PersistentDocumentSetCreationJob) {
    import anorm._
    import anorm.SqlParser._
    import persistence.Schema._
    import org.squeryl.PrimitiveTypeMode._

    Database.inTransaction {
      implicit val connection = Database.currentConnection

      val id = job.documentSetId
      SQL("DELETE FROM document_set_creation_job WHERE document_set_id = {id}").on('id -> id).executeUpdate()
      val uploadedFileId = SQL("SELECT uploaded_file_id FROM document_set WHERE id = {id}").on('id -> id).as(scalar[Option[Long]].single)

      SQL("DELETE FROM node_document WHERE node_id IN (SELECT id FROM node WHERE document_set_id = {id})").on('id -> id).executeUpdate()
      SQL("DELETE FROM node WHERE document_set_id = {id}").on('id -> id).executeUpdate()
      SQL("DELETE FROM document WHERE document_set_id = {id}").on('id -> id).executeUpdate()

      SQL("DELETE FROM document_set WHERE id = {id}").on('id -> id).executeUpdate

      uploadedFileId.map { u =>
        SQL("SELECT lo_unlink(contents_oid) FROM uploaded_file WHERE id = {id}").on('id -> u).as(scalar[Int] *)
        SQL("DELETE FROM uploaded_file WHERE id = {id}").on('id -> u).executeUpdate()
      }
    }
  }
}
