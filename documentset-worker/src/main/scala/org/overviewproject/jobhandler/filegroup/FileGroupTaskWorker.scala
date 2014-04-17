package org.overviewproject.jobhandler.filegroup

import akka.actor._
import akka.pattern.pipe
import org.overviewproject.jobhandler.filegroup.FileGroupTaskWorkerProtocol._
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.concurrent.Future

trait FileGroupTaskStep {
  def execute: FileGroupTaskStep
}

case class FileGroupTaskDone(fileGroupId: Long, uploadedFileId: Long) extends FileGroupTaskStep {
  override def execute: FileGroupTaskStep = return this
}

trait FileGroupTaskWorker extends Actor {
  import context._
  
  protected val jobQueuePath: String

  private val JobQueueId: String = "Job Queue"
  private val RetryInterval: FiniteDuration = 1 second
  
  private val jobQueueSelection = context.actorSelection(jobQueuePath)
  private var jobQueue: ActorRef = _
  
  protected def startTask(documentSetId: Long, fileGroupId: Long, uploadedFileId: Long): FileGroupTaskStep 
  
  lookForJobQueue

  def receive = {
    case ActorIdentity(JobQueueId, Some(jq)) => { 
      jobQueue = jq
      jobQueue ! RegisterWorker(self)
    }
    case ActorIdentity(JobQueueId, None) => 
      system.scheduler.scheduleOnce(RetryInterval) { lookForJobQueue }
    case TaskAvailable =>
      jobQueue ! ReadyForTask
    case Task(documentSetId, fileGroupId, uploadedFileId) =>
      	executeTaskStep(startTask(documentSetId, fileGroupId, uploadedFileId))
    case FileGroupTaskDone(fileGroupId, uploadedFileId) =>
      jobQueue ! TaskDone(fileGroupId, uploadedFileId)
    case step: FileGroupTaskStep => executeTaskStep(step) 
  }
  
  private def lookForJobQueue = jobQueueSelection ! Identify(JobQueueId)
  
  private def executeTaskStep(step: FileGroupTaskStep) = Future { step.execute } pipeTo self
}

