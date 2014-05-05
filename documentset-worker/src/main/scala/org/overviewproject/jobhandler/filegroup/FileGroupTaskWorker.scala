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

trait FileGroupTaskWorker extends Actor {
  import context._
  
  protected def jobQueuePath: String
 
  private val JobQueueId: String = "Job Queue"
  private val RetryInterval: FiniteDuration = 1 second
  
  private val jobQueueSelection = system.actorSelection(jobQueuePath)
  private var jobQueue: ActorRef = _
  
  protected def startCreatePagesTask(documentSetId: Long, fileGroupId: Long, uploadedFileId: Long): FileGroupTaskStep 
  
  lookForJobQueue

  def receive = {
    case ActorIdentity(JobQueueId, Some(jq)) => { 
      Logger.info(s"[${self.path}] Found Job Queue at ${jq.path}")
      jobQueue = jq
      jobQueue ! RegisterWorker(self)
    }
    case ActorIdentity(JobQueueId, None) => 
      Logger.info(s"[${self.path}] Looking for Job Queue at $jobQueuePath")
      system.scheduler.scheduleOnce(RetryInterval) { lookForJobQueue }
    case TaskAvailable =>
      jobQueue ! ReadyForTask
    case CreatePagesTask(documentSetId, fileGroupId, uploadedFileId) => 
      	executeTaskStep(startCreatePagesTask(documentSetId, fileGroupId, uploadedFileId))
    case CreatePagesProcessComplete(documentSetId, fileGroupId, uploadedFileId) => {
      jobQueue ! CreatePagesTaskDone(documentSetId, fileGroupId, uploadedFileId)
      jobQueue ! ReadyForTask
    }
    case step: FileGroupTaskStep => executeTaskStep(step) 
  }
  
  private def lookForJobQueue = jobQueueSelection ! Identify(JobQueueId)
  
  private def executeTaskStep(step: FileGroupTaskStep) = Future { step.execute } pipeTo self
}


object FileGroupTaskWorker {
  def apply(fileGroupJobQueuePath: String): Props = Props( new FileGroupTaskWorker with CreatePagesFromPdfWithStorage {
    override protected def jobQueuePath: String = s"akka://$fileGroupJobQueuePath"
    
  })
}