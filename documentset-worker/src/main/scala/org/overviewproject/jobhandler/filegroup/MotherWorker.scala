package org.overviewproject.jobhandler.filegroup

import akka.actor._
import org.overviewproject.database.Database
import org.overviewproject.database.orm.finders.{ DocumentSetCreationJobFinder, FileFinder, FileGroupFinder, FileUploadFinder }
import org.overviewproject.database.orm.stores.{ DocumentSetCreationJobStore, DocumentSetStore, DocumentSetUserStore }
import org.overviewproject.jobhandler.JobProtocol._
import org.overviewproject.jobhandler.MessageHandlerProtocol._
import org.overviewproject.jobhandler.MessageQueueActorProtocol.StartListening
import org.overviewproject.tree.DocumentSetCreationJobType.FileUpload
import org.overviewproject.tree.Ownership
import org.overviewproject.tree.orm._
import org.overviewproject.tree.orm.DocumentSetCreationJobState.{ NotStarted, Preparing }
import org.overviewproject.tree.orm.FileJobState._
import org.overviewproject.util.Configuration
import org.overviewproject.jobhandler.filegroup.FileGroupMessageHandlerProtocol.{ Command => FileGroupCommand, ProcessFileCommand }

object MotherWorkerProtocol {
  sealed trait Command
  case class StartClusteringCommand(
    fileGroupId: Long,
    title: String,
    lang: String,
    suppliedStopWords: String) extends Command
}

trait FileGroupJobHandlerComponent {
  def createFileGroupMessageHandler(jobMonitor: ActorRef): Props
  val storage: Storage

  trait Storage {
    def findFileGroup(fileGroupId: Long): Option[FileGroup]
    def countFileUploads(fileGroupId: Long): Long
    def countProcessedFiles(fileGroupId: Long): Long
    def findDocumentSetCreationJobByFileGroupId(fileGroupId: Long): Option[DocumentSetCreationJob]

    def storeDocumentSet(title: String, lang: String, suppliedStopWords: String): Long
    def storeDocumentSetUser(documentSetId: Long, userEmail: String): Unit
    def storeDocumentSetCreationJob(documentSetId: Long, fileGroupId: Long, state: DocumentSetCreationJobState.Value, lang: String, suppliedStopWords: String): Long
    def submitDocumentSetCreationJob(documentSetCreationJob: DocumentSetCreationJob): DocumentSetCreationJob
  }
}

object MotherWorkerFSM {
  sealed trait State
  case object Scheduling extends State

  
  sealed trait Data
  case class Daughters(free: List[ActorRef], busy: List[ActorRef], queue: List[FileGroupCommand]) extends Data
}

import MotherWorkerFSM._

trait MotherWorker extends Actor with FSM[State, Data] {
  this: FileGroupJobHandlerComponent =>

  import MotherWorkerProtocol._

  private val NumberOfDaughters = 2
  private val ClusteringQueue = Configuration.messageQueue.clusteringQueueName
  private val FileGroupQueue = Configuration.messageQueue.fileGroupQueueName

  private val fileGroupMessageHandlers = List.fill(NumberOfDaughters)(context.actorOf(createFileGroupMessageHandler(self)))

  startWith(Scheduling, Daughters(fileGroupMessageHandlers, Nil, Nil))

  when(Scheduling) {
    case Event(StartClusteringCommand(fileGroupId, title, lang, suppliedStopWords), _) => {
      storage.findFileGroup(fileGroupId).map { fileGroup =>
        val documentSetId = storage.storeDocumentSet(title, lang, suppliedStopWords)
        storage.storeDocumentSetUser(documentSetId, fileGroup.userEmail)
        val jobState = computeJobState(fileGroup)
        storage.storeDocumentSetCreationJob(documentSetId, fileGroupId, jobState, lang, suppliedStopWords)

        sender ! MessageHandled
      }
      stay
    }

    case Event(command: ProcessFileCommand, daughters: Daughters) => {
      daughters.free match {
        case next :: rest => {
          next ! command
          stay using Daughters(rest, next +: daughters.busy, daughters.queue)
        }
        case Nil => {
          val d = Daughters(daughters.free, daughters.busy, daughters.queue :+ command)

          stay using (d)
        }
      }
    }


    case Event(JobDone(fileGroupId), daughters: Daughters) => {
      storage.findDocumentSetCreationJobByFileGroupId(fileGroupId).map { job =>
        if (fileProcessingComplete(fileGroupId)) storage.submitDocumentSetCreationJob(job)
      }

      if (daughters.queue.isEmpty) {
        val busy = daughters.busy.filterNot(_.path == sender.path)
        val free = sender +: daughters.free
        stay using daughters.copy(free = free, busy = busy)
      } else {
        val nextCommand = daughters.queue.head
        sender ! nextCommand
        stay using daughters.copy(queue = daughters.queue.tail)
      }
    }

  }

  initialize

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
    override def createFileGroupMessageHandler(jobMonitor: ActorRef): Props = FileGroupMessageHandler(jobMonitor)

    override val storage: StorageImpl = new StorageImpl

    class StorageImpl extends Storage {
      def findFileGroup(fileGroupId: Long): Option[FileGroup] = Database.inTransaction {
        FileGroupFinder.byId(fileGroupId).headOption
      }

      def countFileUploads(fileGroupId: Long): Long = Database.inTransaction {
        FileUploadFinder.countsByFileGroup(fileGroupId)
      }

      def countProcessedFiles(fileGroupId: Long): Long = Database.inTransaction {
        FileFinder.byFinishedState(fileGroupId).count
      }

      def findDocumentSetCreationJobByFileGroupId(fileGroupId: Long): Option[DocumentSetCreationJob] =
        Database.inTransaction {
          DocumentSetCreationJobFinder.byFileGroupId(fileGroupId).headOption
        }

      def storeDocumentSet(title: String, lang: String, suppliedStopWords: String): Long = Database.inTransaction {
        val documentSet = DocumentSetStore.insertOrUpdate(DocumentSet(
          title = title,
          lang = lang,
          suppliedStopWords = suppliedStopWords))

        documentSet.id
      }

      def storeDocumentSetUser(documentSetId: Long, userEmail: String): Unit = Database.inTransaction {
        DocumentSetUserStore.insertOrUpdate(DocumentSetUser(documentSetId, userEmail, Ownership.Owner))
      }

      def storeDocumentSetCreationJob(documentSetId: Long, fileGroupId: Long, state: DocumentSetCreationJobState.Value, lang: String, suppliedStopWords: String): Long =
        Database.inTransaction {
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
        Database.inTransaction {
          DocumentSetCreationJobStore.insertOrUpdate(documentSetCreationJob.copy(state = NotStarted))
        }

    }
  }

  def apply(): Props = Props[MotherWorkerImpl]
}