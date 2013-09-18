/**
 *
 * JobHandler.scala
 *
 * Overview Project,June 2012
 * @author Jonas Karlsson
 */

import java.sql.Connection

import scala.util._

import com.jolbox.bonecp._

import org.overviewproject.clone.CloneDocumentSet
import org.overviewproject.clustering.DocumentSetIndexer
import org.overviewproject.database.{ SystemPropertiesDatabaseConfiguration, DataSource, DB }
import org.overviewproject.database.Database
import org.overviewproject.persistence._
import org.overviewproject.tree.DocumentSetCreationJobType
import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import org.overviewproject.util.{ DocumentProducerFactory, ExceptionStatusMessage, JobRestarter, Logger, ThrottledProgressReporter }
import org.overviewproject.util.Progress._
import org.overviewproject.util.SearchIndex

object JobHandler {
  // Run a single job
  def handleSingleJob(j: PersistentDocumentSetCreationJob): Unit = {
    try {
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

        Logger.info(s"[${j.documentSetId}] $logLabel: ${progress.fraction * 100}% done. ${progress.status}, ${if (progress.hasError) "ERROR" else "OK"}")
      }

      val progressReporter = new ThrottledProgressReporter(stateChange = Seq(updateJobState, logProgress), interval = Seq(checkCancellation))

      def progFn(progress: Progress): Boolean = {
        progressReporter.update(progress)
        j.state == Cancelled
      }

      j.jobType match {
        case DocumentSetCreationJobType.Clone => handleCloneJob(j)
        case _ => handleCreationJob(j, progFn)
      }

      Database.inTransaction { j.delete }

    } catch {
      case e: Exception => reportError(j, e)
      case t: Throwable => { // Rethrow (and die) if we get non-Exception throwables, such as java.lang.error
        reportError(j, t)
        DB.close()
        throw (t)
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
    val config = new SystemPropertiesDatabaseConfiguration()
    val dataSource = new DataSource(config)

    DB.connect(dataSource)

    val searchIndexSetup = Try {
      Logger.info("Looking for Search Index")
      SearchIndex.createIndexIfNotExisting
    }

    searchIndexSetup match {
      case Success(v) => {
        Logger.info("Starting to scan for jobs")
        startHandlingJobs
      }
      case Failure(e) => {
        Logger.error("Unable to create Search Index", e)
        throw (e)
      }
    }
  }

  private def startHandlingJobs: Unit = {
    val pollingInterval = 500 //milliseconds

    DB.withConnection { implicit connection =>
      restartInterruptedJobs
    }

    while (true) {
      // Exit when the user enters Ctrl-D
      while (System.in.available > 0) {
        val EOF = 4
        val next = System.in.read
        if (next == EOF) {
          System.exit(0)
        }
      }
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

    Logger.info(s"[${job.documentSetId}] Deleting cancelled job")
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
      DocumentSetLoader.load(job.documentSetId)
    }

    def documentSetInfo(documentSet: Option[DocumentSet]): String = documentSet.map { ds =>
      val query = ds.query.map(q => s"Query: $q").getOrElse("")
      val uploadId = ds.uploadedFileId.map(u => s"UploadId: $u").getOrElse("")

      s"Creating DocumentSet: ${job.documentSetId} Title: ${ds.title} $query $uploadId Splitting: ${job.splitDocuments}".trim
    }.getOrElse(s"Creating DocumentSet: Could not load document set id: ${job.documentSetId}")

    Logger.info(documentSetInfo(documentSet))

    documentSet.map { ds =>
      val nodeWriter = new NodeWriter(job.documentSetId)

      val indexer = new DocumentSetIndexer(nodeWriter, job.lang, job.suppliedStopWords, progressFn)
      val producer = DocumentProducerFactory.create(job, ds, indexer, progressFn)

      producer.produce()
    }

  }

  private def handleCloneJob(job: PersistentDocumentSetCreationJob) {
    import org.overviewproject.clone.{ JobProgressLogger, JobProgressReporter }

    val jobProgressReporter = new JobProgressReporter(job)
    val progressObservers: Seq[Progress => Unit] = Seq(
      jobProgressReporter.updateStatus _,
      JobProgressLogger.apply(job.documentSetId, _: Progress))

    job.sourceDocumentSetId.map { sourceDocumentSetId =>
      Logger.info(s"Creating DocumentSet: ${job.documentSetId} Cloning Source document set id: $sourceDocumentSetId")
      CloneDocumentSet(sourceDocumentSetId, job.documentSetId, job, progressObservers)
    }
  }

  private def reportError(job: PersistentDocumentSetCreationJob, t: Throwable): Unit = {
    Logger.error(s"Job for DocumentSet id ${job.documentSetId} failed: $t\n${t.getStackTrace.mkString("\n")}")
    job.state = Error
    job.statusDescription = Some(ExceptionStatusMessage(t))
    Database.inTransaction {
      job.update
      if (job.state == Cancelled) job.delete
    }
  }
}

