/**
 *
 * JobHandler.scala
 *
 * Overview Project,June 2012
 * @author Jonas Karlsson
 */

import com.jolbox.bonecp._
import org.overviewproject.database.{ DatabaseConfiguration, DataSource, DB }
import java.sql.Connection
import org.overviewproject.persistence._
import org.overviewproject.util.{ DocumentProducerFactory, ExceptionStatusMessage, JobRestarter, Logger, ThrottledProgressReporter }
import org.overviewproject.util.Progress._
import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import org.overviewproject.tree.orm.DocumentSetCreationJobType._
import org.overviewproject.clustering.DocumentSetIndexer
import org.overviewproject.database.Database
import org.overviewproject.http.{ AsyncHttpRequest, DocumentCloudDocumentProducer }
import org.overviewproject.clone.CloneDocumentSet


object JobHandler {

  val asyncHttpRetriever: AsyncHttpRequest = new AsyncHttpRequest
  // Run a single job
  def handleSingleJob(j: PersistentDocumentSetCreationJob): Unit = {
    try {
      Logger.info("Handling job")
      j.state = InProgress
      Database.inTransaction { j.update }
      j.observeCancellation(deleteCancelledJob)

      def checkCancellation(progress: Progress): Unit = Database.inTransaction(j.checkForCancellation)

      def updateJobState(progress: Progress): Unit = {
        j.fractionComplete = progress.fraction
        j.statusDescription = Some(progress.status.toString)
        Database.inTransaction { j.update }
      }
      
      def logProgress(progress: Progress): Unit = {
        val logLabel = if (j.state == Cancelled) "CANCELLED"
        else "PROGRESS"

        Logger.info(logLabel + ": " + progress.fraction * 100 + "% done. " + progress.status + ", " + (if (progress.hasError) "ERROR" else "OK"))
      }
      
      val progressReporter = new ThrottledProgressReporter(stateChange = Seq(updateJobState, logProgress), interval = Seq(checkCancellation))
      
      
      def progFn(progress: Progress): Boolean = {
        progressReporter.update(progress)
        j.state == Cancelled
      }

      j.jobType.value match {
        case CsvImportJob.value | DocumentCloudJob.value => handleCreationJob(j, progFn)
        case CloneJob.value => handleCloneJob(j)
      }

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

    val firstSubmittedJob: Option[PersistentDocumentSetCreationJob] = Database.inTransaction {
      PersistentDocumentSetCreationJob.findFirstJobWithState(NotStarted)
    }

    firstSubmittedJob.map { j =>
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
    import scala.language.postfixOps
    import anorm._
    import anorm.SqlParser._
    import org.overviewproject.persistence.orm.Schema._
    import org.squeryl.PrimitiveTypeMode._

    Database.inTransaction {
      implicit val connection = Database.currentConnection

      val id = job.documentSetId
      SQL("SELECT lo_unlink(contents_oid) FROM document_set_creation_job WHERE document_set_id = {id} AND contents_oid IS NOT NULL").on('id -> id).as(scalar[Int] *)
      SQL("DELETE FROM document_set_creation_job WHERE document_set_id = {id}").on('id -> id).executeUpdate()
      val uploadedFileId = SQL("SELECT uploaded_file_id FROM document_set WHERE id = {id}").on('id -> id).as(scalar[Option[Long]].single)

      SQL("DELETE FROM node_document WHERE node_id IN (SELECT id FROM node WHERE document_set_id = {id})").on('id -> id).executeUpdate()
      SQL("DELETE FROM node WHERE document_set_id = {id}").on('id -> id).executeUpdate()
      SQL("DELETE FROM document WHERE document_set_id = {id}").on('id -> id).executeUpdate()
      SQL("DELETE FROM document_processing_error WHERE document_set_id = {id}").on('id -> id).executeUpdate()

      SQL("DELETE FROM document_set WHERE id = {id}").on('id -> id).executeUpdate()

      uploadedFileId.map { u =>
        SQL("DELETE FROM uploaded_file WHERE id = {id}").on('id -> u).executeUpdate()
      }
    }
  }

  private def handleCreationJob(job: PersistentDocumentSetCreationJob, progressFn: ProgressAbortFn) {
    val documentSet = DB.withConnection { implicit connection =>
      DocumentSetLoader.load(job.documentSetId).get
    }
    val nodeWriter = new NodeWriter(job.documentSetId)

    val indexer = new DocumentSetIndexer(nodeWriter, progressFn)
    val producer = DocumentProducerFactory.create(job, documentSet, indexer, progressFn, asyncHttpRetriever)

    producer.produce()

  }

  private def handleCloneJob(job: PersistentDocumentSetCreationJob) {
    import org.overviewproject.clone.{ JobProgressLogger, JobProgressReporter }
    
    val jobProgressReporter = new JobProgressReporter(job)
    val progressObservers: Seq[Progress => Unit] = Seq(
        jobProgressReporter.updateStatus _,
        JobProgressLogger.apply _
    )
    
    job.sourceDocumentSetId.map { sourceDocumentSetId =>
      CloneDocumentSet(sourceDocumentSetId, job.documentSetId, job, progressObservers)
    }
  }
}

