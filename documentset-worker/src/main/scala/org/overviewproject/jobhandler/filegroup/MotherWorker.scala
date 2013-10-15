package org.overviewproject.jobhandler.filegroup

import scala.collection.mutable.{ ArrayBuffer, Map, Queue }
import akka.actor._
import akka.actor.SupervisorStrategy._
import org.overviewproject.database.Database
import org.overviewproject.database.orm.finders._
import org.overviewproject.database.orm.stores._
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
import org.overviewproject.util.Logger
import scala.concurrent.duration.Duration

object MotherWorkerProtocol {
  sealed trait Command
  case class StartClusteringCommand(
    fileGroupId: Long,
    title: String,
    lang: String,
    suppliedStopWords: String) extends Command
  case class CancelProcessing(fileGroupId: Long) extends Command
}

trait FileGroupJobHandlerComponent {
  def createFileGroupMessageHandler(jobMonitor: ActorRef): Props
  val storage: Storage

  trait Storage {
    def countFileUploads(fileGroupId: Long): Long
    def countProcessedFiles(fileGroupId: Long): Long
    def findDocumentSetCreationJobByFileGroupId(fileGroupId: Long): Option[DocumentSetCreationJob]

    def submitDocumentSetCreationJob(documentSetCreationJob: DocumentSetCreationJob): DocumentSetCreationJob

    def deleteDocumentSetData(fileGroupId: Long): Unit
    def deleteFileGroupData(fileGroupId: Long): Unit
  }
}

/**
 * Manages the workers that process uploaded files.
 * Starts the DocumentSetCreationJob when everything is ready.
 */
trait MotherWorker extends Actor {
  this: FileGroupJobHandlerComponent =>

  import MotherWorkerProtocol._

  private val NumberOfDaughters = 2
  private val ClusteringQueue = Configuration.messageQueue.getString("clustering_commands_queue_name")
  private val FileGroupQueue = Configuration.messageQueue.getString("file_group_queue_name")

  private val freeWorkers = Queue.fill(NumberOfDaughters)(context.actorOf(createFileGroupMessageHandler(self)))
  private val busyWorkers = Map.empty[ActorRef, ProcessFileCommand]
  private val workQueue = Queue.empty[ProcessFileCommand]
  private val cancelledJobs = ArrayBuffer.empty[Long]

  freeWorkers.foreach(context.watch)

  override val supervisorStrategy =
    OneForOneStrategy(0, Duration.Inf) {
      case _: Exception => Stop
      case _: Throwable => Escalate
    }

  def receive = {
    case StartClusteringCommand(fileGroupId, title, lang, suppliedStopWords) =>
      submitCompleteJob(fileGroupId)

    case CancelProcessing(fileGroupId) => {
      workQueue.dequeueAll(c => c.fileGroupId == fileGroupId)
      if (busyWorkers.exists(w => w._2.fileGroupId == fileGroupId))
        cancelledJobs += fileGroupId
      else {
        storage.deleteDocumentSetData(fileGroupId)
        storage.deleteFileGroupData(fileGroupId)
      }
    }

    case command: ProcessFileCommand => {
      if (!freeWorkers.isEmpty) startWork(command)
      else workQueue.enqueue(command)
    }

    case JobDone(fileGroupId) => {
      setFree(sender)

      if (cancelledJobs.exists(_ == fileGroupId)) {
        if (!busyWorkers.exists(w => w._2.fileGroupId == fileGroupId)) {
          cancelledJobs -= fileGroupId
          storage.deleteDocumentSetData(fileGroupId)
          storage.deleteFileGroupData(fileGroupId)
        }
      } else {
        submitCompleteJob(fileGroupId)
      }

      if (!workQueue.isEmpty) self ! workQueue.dequeue
    }

    case Terminated(w) => {
      val newWorker = context.actorOf(createFileGroupMessageHandler(self))
      freeWorkers.enqueue(newWorker)
      freeWorkers.dequeueAll(_ == w)
      busyWorkers.get(w) map { c => self ! c }
      busyWorkers -= w

    }

  }

  /** file processing is complete when number of uploads matches number of processed files */
  private def fileProcessingComplete(fileGroupId: Long): Boolean =
    storage.countFileUploads(fileGroupId) == storage.countProcessedFiles(fileGroupId)

  private def submitCompleteJob(fileGroupId: Long): Unit = {
    if (fileProcessingComplete(fileGroupId)) {
      storage.findDocumentSetCreationJobByFileGroupId(fileGroupId) map {
        storage.submitDocumentSetCreationJob
      }
    }
  }

  private def startWork(command: ProcessFileCommand): Unit = {
    val next = freeWorkers.dequeue()
    busyWorkers += (next -> command)
    next ! command
  }

  private def setFree(worker: ActorRef): Unit = {
    busyWorkers -= worker
    freeWorkers.enqueue(worker)
  }
}

object MotherWorker {
  import org.overviewproject.postgres.SquerylEntrypoint._

  private class MotherWorkerImpl extends MotherWorker with FileGroupJobHandlerComponent {
    override def createFileGroupMessageHandler(jobMonitor: ActorRef): Props = FileGroupMessageHandler(jobMonitor)

    override val storage: StorageImpl = new StorageImpl

    class StorageImpl extends Storage {

      override def countFileUploads(fileGroupId: Long): Long = Database.inTransaction {
        GroupedFileUploadFinder.countsByFileGroup(fileGroupId)
      }

      override def countProcessedFiles(fileGroupId: Long): Long = Database.inTransaction {
        GroupedProcessedFileFinder.byFileGroup(fileGroupId).count
      }

      override def findDocumentSetCreationJobByFileGroupId(fileGroupId: Long): Option[DocumentSetCreationJob] =
        Database.inTransaction {
          DocumentSetCreationJobFinder.byFileGroupId(fileGroupId).headOption
        }

      override def submitDocumentSetCreationJob(documentSetCreationJob: DocumentSetCreationJob): DocumentSetCreationJob =
        Database.inTransaction {
          DocumentSetCreationJobStore.insertOrUpdate(documentSetCreationJob.copy(state = NotStarted))
        }

      override def deleteDocumentSetData(fileGroupId: Long): Unit = Database.inTransaction {
        DocumentSetCreationJobFinder.byFileGroupId(fileGroupId).headOption.map { job =>
          DocumentSetStore.delete(job.documentSetId)
          DocumentSetCreationJobStore.delete(job.id)
        }
      }

      override def deleteFileGroupData(fileGroupId: Long): Unit = Database.inTransaction {
        GroupedFileUploadStore.deleteUnprocessedUploadsAndContents(fileGroupId)
        GroupedProcessedFileStore.deleteWithContentsByFileGroup(fileGroupId)
      }

    }
  }

  def apply(): Props = Props[MotherWorkerImpl]
}