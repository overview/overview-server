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

import org.overviewproject.clone.CloneDocumentSet
import org.overviewproject.clustering.{ DocumentSetIndexer, DocumentSetIndexerOptions }
import org.overviewproject.database.{ DatabaseConfiguration, DeprecatedDatabase, DataSource, DB, HasBlockingDatabase }
import org.overviewproject.persistence.{ NodeWriter, PersistentDocumentSetCreationJob }
import org.overviewproject.persistence.TreeIdGenerator
import org.overviewproject.tree.DocumentSetCreationJobType
import org.overviewproject.tree.orm.DocumentSetCreationJobState
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.models.{ DocumentSet, Tree }
import org.overviewproject.models.tables.{DocumentSets,Trees}
import org.overviewproject.util._
import org.overviewproject.util.Progress._

object JobHandler extends HasBlockingDatabase {
  import database.api._
  private val logger = Logger.forClass(getClass)

  def main(args: Array[String]) {
    // Make sure java.sql.Timestamp values are correct
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    val config = DatabaseConfiguration.fromConfig
    val dataSource = DataSource(config)

    DB.connect(dataSource)

    logger.info("Starting to scan for jobs")
    startHandlingJobs
  }

  private def startHandlingJobs: Unit = {
    val pollingInterval = 500 //milliseconds

    restartInterruptedJobs

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

  // Run each job currently listed in the database
  private def scanForJobs: Unit = {

    val firstSubmittedJob: Option[PersistentDocumentSetCreationJob] = DeprecatedDatabase.inTransaction {
      PersistentDocumentSetCreationJob.findFirstJobWithState(DocumentSetCreationJobState.NotStarted)
    }

    firstSubmittedJob.map { j =>
      logger.info(s"Processing job: [${j.id}] ${j.documentSetId}")
      handleSingleJob(j)
      System.gc()
    }
  }

  // Run a single job
  private def handleSingleJob(j: PersistentDocumentSetCreationJob): Unit = {
    // Helper functions used to track progress and monitor/update job state

    def checkCancellation(progress: Progress): Unit = DeprecatedDatabase.inTransaction(j.checkForCancellation)

    def updateJobState(progress: Progress): Unit = {
      j.fractionComplete = progress.fraction
      j.statusDescription = Some(progress.status.toString)
      DeprecatedDatabase.inTransaction { j.update }
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
      j.state = DocumentSetCreationJobState.InProgress
      DeprecatedDatabase.inTransaction { j.update }
      j.observeCancellation(deleteCancelledJob)

      j.jobType match {
        case DocumentSetCreationJobType.Clone => handleCloneJob(j)
        case _ => handleCreationJob(j, progFn)
      }

      logger.info(s"Cleaning up job ${j.documentSetId}")
      blockingDatabase.runUnit(sqlu"""
        WITH a AS (
          DELETE FROM document_set_creation_job_node
          WHERE document_set_creation_job_id = ${j.id}
        )
        DELETE FROM document_set_creation_job WHERE id = ${j.id}
      """)
    } catch {
      case e: Exception => reportError(j, e)
      case t: Throwable => { // Rethrow (and die) if we get non-Exception throwables, such as java.lang.error
        reportError(j, t)
        DB.close()
        throw (t)
      }
    }
  }

  private def handleCreationJob(job: PersistentDocumentSetCreationJob, progressFn: ProgressAbortFn): Unit = {
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

      val treeId = TreeIdGenerator.next(ds.id)

      val nodeWriter = new NodeWriter(job.id, treeId)

      val opts = DocumentSetIndexerOptions(job.lang, job.suppliedStopWords, job.importantWords)

      val indexer = new DocumentSetIndexer(nodeWriter, opts, progressFn)
      val producer = DocumentProducerFactory.create(job, ds, indexer, progressFn)

      val numberOfDocuments = producer.produce()

      if (job.state != DocumentSetCreationJobState.Cancelled) {
        if (job.jobType == DocumentSetCreationJobType.Recluster)
          createTree(treeId, nodeWriter.rootNodeId, ds, numberOfDocuments, job)
        else submitClusteringJob(ds.id)
      }

      val t3 = System.currentTimeMillis()
      logger.info("Created DocumentSet {}. cluster {}ms; total {}ms", ds.id, t3 - t2, t3 - t1)
    }
  }

  private def handleCloneJob(job: PersistentDocumentSetCreationJob): Unit = {
    import org.overviewproject.clone.{ JobProgressLogger, JobProgressReporter }

    val jobProgressReporter = new JobProgressReporter(job)
    val progressObservers: Seq[Progress => Unit] = Seq(
      jobProgressReporter.updateStatus _,
      JobProgressLogger.apply(job.documentSetId, _: Progress))

    job.sourceDocumentSetId.map { sourceDocumentSetId =>
      logger.info(s"Creating DocumentSet: ${job.documentSetId} Cloning Source document set id: $sourceDocumentSetId")
      CloneDocumentSet(sourceDocumentSetId, job.documentSetId, job, progressObservers)
      verifySourceStillExists(sourceDocumentSetId)
    }
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

  private def restartInterruptedJobs: Unit = JobRestarter.restartInterruptedJobs

  private def deleteCancelledJob(job: PersistentDocumentSetCreationJob) {
    import database.api._
    import database.executionContext

    logger.info("Deleting cancelled job {} for DocumentSet {}", job.id, job.documentSetId)

    blockingDatabase.runUnit(sqlu"""
      WITH a AS (
        SELECT lo_unlink(contents_oid)
        FROM document_set_creation_job
        WHERE document_set_id = ${job.documentSetId}
          AND contents_oid IS NOT NULL
      ), b AS (
        DELETE FROM document_set_creation_job_node
        WHERE document_set_creation_job_id = ${job.id}
      )
      DELETE FROM document_set_creation_job WHERE id = ${job.id}
    """)
  }

  private def reportError(job: PersistentDocumentSetCreationJob, t: Throwable): Unit = {
    t match {
      case e: DisplayedError => logger.info("Handled error for DocumentSet {} creation: {}", job.documentSetId, e)
      case NonFatal(e) => {
        logger.warn("Evil error for DocumentSet {} creation: {}", job.documentSetId, e)
        logger.error("Evil error details:", e)
      }
    }

    job.state = DocumentSetCreationJobState.Error
    job.statusDescription = Some(ExceptionStatusMessage(t))
    DeprecatedDatabase.inTransaction {
      job.update
      if (job.state == DocumentSetCreationJobState.Cancelled) job.delete
    }
  }

  private def findDocumentSet(documentSetId: Long): Option[DocumentSet] = {
    blockingDatabase.option(DocumentSets.filter(_.id === documentSetId))
  }

  private def createTree(treeId: Long, rootNodeId: Long, documentSet: DocumentSet,
                         numberOfDocuments: Int, job: PersistentDocumentSetCreationJob): Unit = {
    blockingDatabase.runUnit(Trees.+=(Tree(
      id = treeId,
      documentSetId = documentSet.id,
      rootNodeId = rootNodeId,
      jobId = job.id,
      title = job.treeTitle.getOrElse("Tree"), // FIXME: Translate by making treeTitle a String instead of Option[String]
      documentCount = numberOfDocuments,
      lang = job.lang,
      description = job.treeDescription.getOrElse(""),
      suppliedStopWords = job.suppliedStopWords.getOrElse(""),
      importantWords = job.importantWords.getOrElse(""),
      createdAt = new java.sql.Timestamp(System.currentTimeMillis)
    )))
  }

  // FIXME: Submitting jobs, along with creating documents should move into documentset-worker
  private def submitClusteringJob(documentSetId: Long): Unit = DeprecatedDatabase.inTransaction {
    import database.api._
    import org.overviewproject.postgres.SquerylEntrypoint._
    import org.overviewproject.persistence.orm.Schema.documentSetCreationJobs
    import org.overviewproject.tree.orm.finders.DocumentSetComponentFinder
    import org.overviewproject.tree.orm.DocumentSetCreationJob

    val documentSetCreationJobStore = BaseStore(documentSetCreationJobs)
    val documentSetCreationJobFinder = DocumentSetComponentFinder(documentSetCreationJobs)

    for {
      documentSet <- blockingDatabase.option(DocumentSets.filter(_.id === documentSetId))
      job <- documentSetCreationJobFinder.byDocumentSet(documentSetId).forUpdate.headOption if (job.state != DocumentSetCreationJobState.Cancelled)
    } {
      val clusteringJob = DocumentSetCreationJob(
        documentSetId = documentSet.id,
        treeTitle = Some("Tree"), // FIXME: Translate by making treeTitle come from a job
        jobType = DocumentSetCreationJobType.Recluster,
        lang = job.lang,
        suppliedStopWords = job.suppliedStopWords,
        importantWords = job.importantWords,
        contentsOid = job.contentsOid, // FIXME: should be deleted when we delete original job
        splitDocuments = job.splitDocuments,
        state = DocumentSetCreationJobState.NotStarted)

      documentSetCreationJobStore.insertOrUpdate(clusteringJob)
      documentSetCreationJobStore.delete(job.id)
    }
  }

}
