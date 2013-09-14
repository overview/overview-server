package org.overviewproject.jobhandler.filegroup

import akka.actor._
import org.overviewproject.jobhandler.filegroup.FileGroupJobHandlerProtocol.ListenForFileGroupJobs
import org.overviewproject.tree.orm.DocumentSetCreationJobState
import org.overviewproject.tree.orm.FileGroup
import org.overviewproject.tree.orm.DocumentSet
import org.overviewproject.tree.orm.DocumentSetCreationJob
import org.overviewproject.tree.DocumentSetCreationJobType.FileUpload
import org.overviewproject.tree.orm.DocumentSetCreationJobState.{ NotStarted, Preparing }
import org.overviewproject.tree.orm.FileJobState._
import org.overviewproject.jobhandler.JobDone
import org.overviewproject.database.orm.finders.FileFinder
import org.overviewproject.database.orm.finders.FileGroupFinder
import org.overviewproject.database.orm.finders.FileUploadFinder
import org.overviewproject.database.orm.stores.DocumentSetStore
import org.overviewproject.database.orm.stores.DocumentSetCreationJobStore
import org.overviewproject.database.orm.finders.DocumentSetCreationJobFinder

object MotherWorkerProtocol {
  sealed trait Command
  case class StartClusteringCommand(
    fileGroupId: Long,
    title: String,
    lang: String,
    suppliedStopWords: String) extends Command
}

trait FileGroupJobHandlerComponent {
  def createFileGroupJobHandler: Props
  val storage: Storage

  trait Storage {
    def findFileGroup(fileGroupId: Long): Option[FileGroup]
    def countFileUploads(fileGroupId: Long): Long
    def countProcessedFiles(fileGroupId: Long): Long
    def findDocumentSetCreationJobByFileGroupId(fileGroupId: Long): Option[DocumentSetCreationJob]

    def storeDocumentSet(title: String, lang: String, suppliedStopWords: String): Long
    def storeDocumentSetCreationJob(documentSetId: Long, fileGroupId: Long, state: DocumentSetCreationJobState.Value, lang: String, suppliedStopWords: String): Long
    def submitDocumentSetCreationJob(documentSetCreationJob: DocumentSetCreationJob): DocumentSetCreationJob
  }
}

class MotherWorker extends Actor {
  this: FileGroupJobHandlerComponent =>

  import MotherWorkerProtocol._

  private val fileGroupJobHandlers: Seq[ActorRef] = for (i <- 1 to 2) yield {
    val handler = context.actorOf(createFileGroupJobHandler)
    handler ! ListenForFileGroupJobs

    handler
  }

  def receive = {
    case StartClusteringCommand(fileGroupId, title, lang, suppliedStopWords) =>
      storage.findFileGroup(fileGroupId).map { fileGroup =>
        val documentSetId = storage.storeDocumentSet(title, lang, suppliedStopWords)
        val jobState = computeJobState(fileGroup)
        storage.storeDocumentSetCreationJob(documentSetId, fileGroupId, jobState, lang, suppliedStopWords)
      }
    case JobDone(fileGroupId) => storage.findDocumentSetCreationJobByFileGroupId(fileGroupId).map { job =>
      if (fileProcessingComplete(fileGroupId)) storage.submitDocumentSetCreationJob(job)
    }

  }

  /**
   * If all files have been uploaded, and all uploaded files have been processed,
   * the documentSetCreationJob state is `NotStarted` (ready for clustering). Otherwise the state
   * is `Preparing`
   */
  private def computeJobState(fileGroup: FileGroup): DocumentSetCreationJobState.Value =
    if ((fileGroup.state == Complete) && fileProcessingComplete(fileGroup.id)) NotStarted
    else Preparing

  /** file processing is complete when number of uploads matches number of processed files */
  private def fileProcessingComplete(fileGroupId: Long): Boolean =
    storage.countFileUploads(fileGroupId) == storage.countProcessedFiles(fileGroupId)
}

object MotherWorker {
  private class MotherWorkerImpl extends MotherWorker with FileGroupJobHandlerComponent {
    override def createFileGroupJobHandler: Props = FileGroupJobHandler()

    override val storage: StorageImpl = new StorageImpl

    class StorageImpl extends Storage {
      def findFileGroup(fileGroupId: Long): Option[FileGroup] = FileGroupFinder.byId(fileGroupId).headOption

      def countFileUploads(fileGroupId: Long): Long = FileUploadFinder.countsByFileGroup(fileGroupId)

      def countProcessedFiles(fileGroupId: Long): Long = FileFinder.countByFinishedState(fileGroupId)

      def findDocumentSetCreationJobByFileGroupId(fileGroupId: Long): Option[DocumentSetCreationJob] =
        DocumentSetCreationJobFinder.byFileGroupId(fileGroupId).headOption

      def storeDocumentSet(title: String, lang: String, suppliedStopWords: String): Long = {
        val documentSet = DocumentSetStore.insertOrUpdate(DocumentSet(
          title = title,
          lang = lang,
          suppliedStopWords = suppliedStopWords))

        documentSet.id
      }

      def storeDocumentSetCreationJob(documentSetId: Long, fileGroupId: Long, state: DocumentSetCreationJobState.Value, lang: String, suppliedStopWords: String): Long = {
        val documentSetCreationJob = DocumentSetCreationJobStore.insertOrUpdate(DocumentSetCreationJob(
          documentSetId = documentSetId,
          jobType = FileUpload,
          lang = lang,
          suppliedStopWords = suppliedStopWords,
          fileGroupId = Some(fileGroupId),
          state = state))
        documentSetCreationJob.id
      }

      def submitDocumentSetCreationJob(documentSetCreationJob: DocumentSetCreationJob): DocumentSetCreationJob = 
        DocumentSetCreationJobStore.insertOrUpdate(documentSetCreationJob.copy(state = NotStarted))
    }
  }
  
  def apply(): Props = Props[MotherWorkerImpl]
}