/**
 *
 * JobHandler.scala
 *
 * Overview,June 2012
 * @author Jonas Karlsson
 */

import java.util.TimeZone
import scala.annotation.tailrec
import scala.util._
import scala.util.control.NonFatal
import scala.concurrent.{Await,Future,blocking}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

import com.overviewdocs.clone.DocumentSetCloner
import com.overviewdocs.database.{DB,DanglingNodeDeleter,HasBlockingDatabase,TreeIdGenerator}
import com.overviewdocs.persistence.NodeWriter
import com.overviewdocs.models.{DocumentSet,DocumentSetCreationJob,DocumentSetCreationJobState,DocumentSetCreationJobType,Tree}
import com.overviewdocs.models.tables.{DocumentSets,DocumentSetCreationJobs,Trees}
import com.overviewdocs.util._
import com.overviewdocs.util.Progress._

object JobHandler extends HasBlockingDatabase {
  import database.api._
  private val logger = Logger.forClass(getClass)

  private def await[T](future: Future[T]): T = blocking(Await.result(future, Duration.Inf))

  def main(args: Array[String]) {
    // Make sure java.sql.Timestamp values are correct
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    // Connect to the database
    DB.dataSource

    logger.info("Cleaning up dangling nodes")
    await(DanglingNodeDeleter.run)

    logger.info("Starting to scan for jobs")
    startHandlingJobs
  }

  private def startHandlingJobs: Unit = {
    val pollingInterval = 500 //milliseconds

    JobRestarter.restartInterruptedJobs

    while (true) {
      handleJobs
      handleTrees
      Thread.sleep(pollingInterval)
    }
  }

  // Run each job currently listed in the database
  private def handleJobs: Unit = {
    blockingDatabase.seq(DocumentSetCreationJobs.filter(_.state === DocumentSetCreationJobState.NotStarted))
      .foreach { job =>
        logger.info("Processing job {}", job)
        handleSingleJob(job)
      }
  }

  // Build each tree currently listed in the database
  private def handleTrees: Unit = {
    blockingDatabase.seq(Trees.filter(_.progress =!= 1.0))
      .foreach { tree =>
        logger.info("Processing tree {}", tree)
        val runner = new com.overviewdocs.clustering.Runner(tree)
        runner.runBlocking
      }
  }

  // Run a single job
  private def handleSingleJob(job: DocumentSetCreationJob): Unit = {
    import database.api._

    var j = job // You can tell this is unmaintainable by the variable name.

    def checkCancellation(progress: Progress): Unit = {
      // Overwrite the Cancelled state
      j = blockingDatabase.option(DocumentSetCreationJobs.filter(_.id === job.id)).getOrElse(job.copy(state=DocumentSetCreationJobState.Cancelled))
    }

    def updateJobState(progress: Progress): Unit = {
      blockingDatabase.runUnit(sqlu"""
        UPDATE document_set_creation_job
        SET fraction_complete = ${progress.fraction}, status_description = ${progress.status.toString}
        WHERE id = ${job.id}
      """)
    }

    def logProgress(progress: Progress): Unit = {
      val logLabel = if (j.state == DocumentSetCreationJobState.Cancelled) "CANCELLED"
      else "PROGRESS"

      logger.info(s"[${j.documentSetId}] $logLabel: ${progress.fraction * 100}% done. ${progress.status}, ${if (progress.hasError) "ERROR" else "OK"}")
    }

    val progressReporter = new ThrottledProgressReporter(stateChange = Seq(updateJobState, logProgress), interval = Seq(checkCancellation))
    def progFn(progress: Progress): Boolean = {
      progressReporter.update(progress)
      j.state == DocumentSetCreationJobState.Cancelled
    }

    try {
      blockingDatabase.runUnit(sqlu"""
        UPDATE document_set_creation_job
        SET state = ${DocumentSetCreationJobState.InProgress.id}
        WHERE id = ${job.id}
      """)

      j.jobType match {
        case DocumentSetCreationJobType.Clone => handleCloneJob(j)
        case _ => handleCreationJob(j, progFn)
      }

      logger.info(s"Cleaning up job ${j.documentSetId}")
      blockingDatabase.runUnit(sqlu"""
        WITH a AS (
          SELECT lo_unlink(contents_oid)
          FROM document_set_creation_job
          WHERE document_set_id = ${job.documentSetId}
            AND contents_oid IS NOT NULL
        )
        DELETE FROM document_set_creation_job WHERE id = ${job.id}
      """)
    } catch {
      case e: Exception => reportError(j, e)
      case t: Throwable => { // Rethrow (and die) if we get non-Exception throwables, such as java.lang.error
        reportError(j, t)
        throw (t)
      }
    }
  }

  private def handleCreationJob(job: DocumentSetCreationJob, progressFn: ProgressAbortFn): Unit = {
    val documentSet = findDocumentSet(job.documentSetId)

    def documentSetInfo(documentSet: Option[DocumentSet]): String = documentSet.map { ds =>
      val query = ds.query.map(q => s"Query: $q").getOrElse("")
      val uploadId = ds.uploadedFileId.map(u => s"UploadId: $u").getOrElse("")

      s"Creating DocumentSet: ${job.documentSetId} Title: ${ds.title} $query $uploadId Splitting: ${job.splitDocuments}".trim
    }.getOrElse(s"Creating DocumentSet: Could not load document set id: ${job.documentSetId}")

    logger.info(documentSetInfo(documentSet))

    documentSet.map { ds =>
      val t1 = ds.createdAt.getTime()
      val t2 = System.currentTimeMillis()

      val producer = DocumentProducerFactory.create(job, ds, progressFn)

      producer.produce()

      if (job.state != DocumentSetCreationJobState.Cancelled) {
        submitClusteringJob(job)

        val t3 = System.currentTimeMillis()
        logger.info("Created DocumentSet {}. cluster {}ms; total {}ms", ds.id, t3 - t2, t3 - t1)
      }
    }
  }

  private def handleCloneJob(job: DocumentSetCreationJob): Unit = {
    DocumentSetCloner.run(job)
  }

  // If source document set has been deleted during the cloning process
  // we can't guarantee that all data was cloned.
  // If the source has been deleted, we throw an exception, which ends up as an error
  // that the user can see, explaining why the cloning failed.
  private def verifySourceStillExists(sourceDocumentSetId: Long): Unit = {
    import database.api._

    val count = blockingDatabase.length(
      DocumentSets.filter(ds => ds.id === sourceDocumentSetId && ds.deleted === false)
    )

    if (count == 0) {
      throw new DisplayedError("source_documentset_deleted")
    }
  }

  private def reportError(job: DocumentSetCreationJob, t: Throwable): Unit = {
    import database.api._

    t match {
      case e: DisplayedError => logger.info("Handled error for DocumentSet {} creation: {}", job.documentSetId, e)
      case NonFatal(e) => {
        logger.error("Evil error for DocumentSet {} creation: {}", job.documentSetId, e)
      }
    }

    blockingDatabase.runUnit(sqlu"""
      UPDATE document_set_creation_job
      SET state = ${DocumentSetCreationJobState.Cancelled.id}
      WHERE id = ${job.id}
    """)
  }

  private def findDocumentSet(documentSetId: Long): Option[DocumentSet] = {
    blockingDatabase.option(DocumentSets.filter(_.id === documentSetId))
  }

  private def submitClusteringJob(job: DocumentSetCreationJob): Unit = {
    import database.api._

    val treeId = await(TreeIdGenerator.next(job.documentSetId))

    blockingDatabase.runUnit(Trees.+=(Tree.CreateAttributes(
      documentSetId=job.documentSetId,
      lang=job.lang
    ).toTreeWithId(treeId)))
  }
}
