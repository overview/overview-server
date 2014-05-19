package org.overviewproject.jobhandler.filegroup

import akka.actor._
import akka.pattern.pipe
import org.overviewproject.jobhandler.filegroup.FileGroupTaskWorkerProtocol._
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.concurrent.Future
import org.overviewproject.util.Logger

trait FileGroupTaskStep {
  def execute: FileGroupTaskStep
}

case class CreatePagesProcessComplete(documentSetId: Long, fileGroupId: Long, uploadedFileId: Long) extends FileGroupTaskStep {
  override def execute: FileGroupTaskStep = return CreatePagesProcessComplete.this
}

object FileGroupTaskWorkerFSM {
  sealed trait State
  case object LookingForJobQueue extends State
  case object Ready extends State
  case object Working extends State
  case object Cancelled extends State

  sealed trait Data
  case object NoKnownJobQueue extends Data
  case class JobQueue(queue: ActorRef) extends Data
  case class TaskInfo(queue: ActorRef, documentSetId: Long, fileGroupId: Long, uploadedFileId: Long) extends Data

}

import FileGroupTaskWorkerFSM._

trait FileGroupTaskWorker extends Actor with FSM[State, Data] {
  import context._

  protected def jobQueuePath: String

  private val JobQueueId: String = "Job Queue"
  private val RetryInterval: FiniteDuration = 1 second

  private val jobQueueSelection = system.actorSelection(jobQueuePath)
  private var jobQueue: ActorRef = _

  protected def startCreatePagesTask(documentSetId: Long, fileGroupId: Long, uploadedFileId: Long): FileGroupTaskStep

  lookForJobQueue

  startWith(LookingForJobQueue, NoKnownJobQueue)

  when(LookingForJobQueue) {
    case Event(ActorIdentity(JobQueueId, Some(jq)), _) => {
      Logger.info(s"[${self.path}] Found Job Queue at ${jq.path}")
      jq ! RegisterWorker(self)

      goto(Ready) using JobQueue(jq)
    }
    case Event(ActorIdentity(JobQueueId, None), _) => {
      Logger.info(s"[${self.path}] Looking for Job Queue at $jobQueuePath")
      system.scheduler.scheduleOnce(RetryInterval) { lookForJobQueue }

      stay
    }
  }

  when(Ready) {
    case Event(TaskAvailable, JobQueue(jobQueue)) => {
      jobQueue ! ReadyForTask
      stay
    }
    case Event(CreatePagesTask(documentSetId, fileGroupId, uploadedFileId), JobQueue(jobQueue)) => {
      executeTaskStep(startCreatePagesTask(documentSetId, fileGroupId, uploadedFileId))
      goto(Working) using TaskInfo(jobQueue, documentSetId, fileGroupId, uploadedFileId)
    }
  }

  when(Working) {
    case Event(CreatePagesProcessComplete(documentSetId, fileGroupId, uploadedFileId), TaskInfo(jobQueue, _, _, _)) => {
      jobQueue ! CreatePagesTaskDone(documentSetId, fileGroupId, uploadedFileId)
      jobQueue ! ReadyForTask

      goto(Ready) using JobQueue(jobQueue)
    }
    case Event(step: FileGroupTaskStep, _) => {
      executeTaskStep(step)

      stay
    }
    case Event(CancelTask, _) => goto(Cancelled)
  }

  when(Cancelled) {
    case Event(step: FileGroupTaskStep, TaskInfo(jobQueue, documentSetId, fileGroupId, uploadedFileId)) => {
      jobQueue ! CreatePagesTaskDone(documentSetId, fileGroupId, uploadedFileId)
      jobQueue ! ReadyForTask

      goto(Ready) using JobQueue(jobQueue)
    }
  }

  private def lookForJobQueue = jobQueueSelection ! Identify(JobQueueId)

  private def executeTaskStep(step: FileGroupTaskStep) = Future { step.execute } pipeTo self
}

object FileGroupTaskWorker {
  def apply(fileGroupJobQueuePath: String): Props = Props(new FileGroupTaskWorker with CreatePagesFromPdfWithStorage {
    override protected def jobQueuePath: String = s"akka://$fileGroupJobQueuePath"

  })
}