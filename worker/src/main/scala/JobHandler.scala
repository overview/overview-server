/**
 *
 * JobHandler.scala
 *
 * Overview Project,June 2012
 * @author Jonas Karlsson
 */

import java.sql.Connection
import scala.annotation.tailrec
import scala.util._
import org.elasticsearch.ElasticSearchException
import org.overviewproject.clone.CloneDocumentSet
import org.overviewproject.clustering.{ DocumentSetIndexer, DocumentSetIndexerOptions }
import org.overviewproject.database.{ SystemPropertiesDatabaseConfiguration, Database, DataSource, DB }
import org.overviewproject.persistence._
import org.overviewproject.persistence.orm.finders.{ DocumentFinder, FileFinder, FileGroupFinder, GroupedFileUploadFinder }
import org.overviewproject.persistence.orm.stores.{ FileStore, FileGroupStore, GroupedFileUploadStore }
import org.overviewproject.tree.DocumentSetCreationJobType
import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import org.overviewproject.util._
import org.overviewproject.util.Progress._
import com.jolbox.bonecp._
import org.overviewproject.util.SearchIndex
import org.overviewproject.nlp.DocumentVectorTypes.TermWeight
import org.overviewproject.tree.orm.Tree
import org.overviewproject.tree.orm.stores.BaseStore
import org.overviewproject.persistence.orm.Schema
import org.overviewproject.tree.orm.stores.NoInsertOrUpdate
import org.overviewproject.persistence.orm.stores.TreeStore
import org.overviewproject.tree.orm.DocumentSet

object JobHandler {

  def main(args: Array[String]) {
    val config = new SystemPropertiesDatabaseConfiguration()
    val dataSource = new DataSource(config)

    DB.connect(dataSource)

    connectToSearchIndex
    Logger.info("Starting to scan for jobs")
    startHandlingJobs
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

  // Run each job currently listed in the database
  private def scanForJobs: Unit = {

    val firstSubmittedJob: Option[PersistentDocumentSetCreationJob] = Database.inTransaction {
      PersistentDocumentSetCreationJob.findFirstJobWithState(NotStarted)
    }

    firstSubmittedJob.map { j =>
      Logger.info(s"Processing job: ${j.documentSetId}")
      handleSingleJob(j)
      System.gc()
    }
  }

  // Run a single job
  private def handleSingleJob(j: PersistentDocumentSetCreationJob): Unit = {
    // Helper functions used to track progress and monitor/update job state

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

    try {
      j.state = InProgress
      Database.inTransaction { j.update }
      j.observeCancellation(deleteCancelledJob)

      j.jobType match {
        case DocumentSetCreationJobType.Clone => handleCloneJob(j)
        case _ => handleCreationJob(j, progFn)
      }

      Logger.info(s"Cleaning up job ${j.documentSetId}")
      Database.inTransaction {
        j.delete
        deleteFileGroupData(j)
      }

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

    Logger.info(documentSetInfo(documentSet))

    documentSet.map { ds =>

      val tree = createTree(ds, job)

      val nodeWriter = new NodeWriter(job.documentSetId, tree.id)

      val opts = DocumentSetIndexerOptions(job.lang, job.suppliedStopWords, job.importantWords)

      val indexer = new DocumentSetIndexer(nodeWriter, opts, progressFn)
      val producer = DocumentProducerFactory.create(job, ds, indexer, progressFn)

      val numberOfDocuments = producer.produce()
      
      updateTreeDocumentCount(tree, numberOfDocuments)
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

  private def restartInterruptedJobs(implicit c: Connection) {
    Database.inTransaction {
      val interruptedJobs = PersistentDocumentSetCreationJob.findJobsWithState(InProgress)
      val restarter = new JobRestarter(new DocumentSetCleaner)

      restarter.restart(interruptedJobs)
    }
  }

  @tailrec
  private def connectToSearchIndex: Unit = {
    val SearchIndexRetryInterval = 5000

    Logger.info("Looking for Search Index")
    val attempt = Try {
      SearchIndex.createIndexIfNotExisting
    }

    attempt match {
      case Success(v) => Logger.info("Found Search Index")
      case Failure(e) => {
        Logger.error("Unable to create Search Index", e)
        Thread.sleep(SearchIndexRetryInterval)
        connectToSearchIndex
      }
    }
  }

  private def deleteCancelledJob(job: PersistentDocumentSetCreationJob) {
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

      deleteFileGroupData(job)
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

  private def deleteFileGroupData(job: PersistentDocumentSetCreationJob): Unit = {
    job.fileGroupId.map { fileGroupId =>
      FileStore.delete(FileFinder.byFileGroup(fileGroupId).toQuery)
      GroupedFileUploadStore.delete(GroupedFileUploadFinder.byFileGroup(fileGroupId).toQuery)

      FileGroupStore.delete(FileGroupFinder.byId(fileGroupId).toQuery)
    }
  }

  private def createTree(documentSet: DocumentSet, job: PersistentDocumentSetCreationJob): Tree = {
    val ids = new DocumentSetIdGenerator(job.documentSetId)
    val tree = Tree(ids.next, job.documentSetId, documentSet.title, 0,
      job.lang, job.suppliedStopWords.getOrElse(""), job.importantWords.getOrElse(""))
    Database.inTransaction {
      TreeStore.insert(tree)
    }

    tree
  }

  private def updateTreeDocumentCount(tree: Tree, documentCount: Int): Tree = Database.inTransaction {
    TreeStore.update(tree.copy(documentCount = documentCount))
  }
  
  private def findDocumentSet(documentSetId: Long): Option[DocumentSet] = Database.inTransaction {
    import org.overviewproject.postgres.SquerylEntrypoint._
    import org.overviewproject.persistence.orm.Schema.documentSets
    
    from(documentSets)(ds =>
      where(ds.id === documentSetId)
      select(ds)
    ).headOption
  } 
    
    
}

