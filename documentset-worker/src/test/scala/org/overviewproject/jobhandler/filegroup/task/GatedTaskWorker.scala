package org.overviewproject.jobhandler.filegroup.task

import scala.language.postfixOps
import scala.concurrent.{ Await, Future, Promise }
import scala.concurrent.duration._
import org.overviewproject.test.ParameterStore
import akka.actor.ActorRef

object GatedTaskWorkerProtocol {
  case object CancelYourself
  case object CompleteTaskStep
}

class GatedTaskWorker(jobQueuePath: String, 
    progressReporterPath: String, 
    fileRemovalQueuePath: String,
    fileGroupRemovalQueuePath: String,
    cancelFn: ParameterStore[Unit]) extends FileGroupTaskWorker {

  import GatedTaskWorkerProtocol._
  import FileGroupTaskWorkerProtocol._

  private val taskGate: Promise[Unit] = Promise()

  private class GatedTask(gate: Future[Unit]) extends FileGroupTaskStep {
    override def execute: FileGroupTaskStep = {
      Await.result(gate, 5000 millis)
      this
    }
    
    override def cancel: Unit = cancelFn.store(())
  }
  
  override protected val jobQueueSelection = context.actorSelection(jobQueuePath)
  override protected val progressReporterSelection = context.actorSelection(progressReporterPath)
  override protected val fileRemovalQueue = context.actorSelection(fileRemovalQueuePath)
  override protected val fileGroupRemovalQueue = context.actorSelection(fileGroupRemovalQueuePath)
  
  override protected def startCreatePagesTask(documentSetId: Long, uploadedFileId: Long): FileGroupTaskStep =
    new GatedTask(taskGate.future)

  override protected def startCreateDocumentsTask(documentSetId: Long, splitDocuments: Boolean, progressReporter: ActorRef): FileGroupTaskStep = 
    new GatedTask(taskGate.future)
  
  override protected def startDeleteFileUploadJob(documentSetId: Long, fileGroupId: Long): FileGroupTaskStep = 
    new GatedTask(taskGate.future)
    

  private def manageTaskGate: PartialFunction[Any, Unit] = {
    case CancelYourself => self ! CancelTask
    case CompleteTaskStep => taskGate.success(())
  }

  override def receive = manageTaskGate orElse super.receive
}


