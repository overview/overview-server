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

class GatedTaskWorker(override protected val jobQueuePath: String, 
    override protected val progressReporterPath: String, cancelFn: ParameterStore[Unit]) extends FileGroupTaskWorker {

  import GatedTaskWorkerProtocol._
  import FileGroupTaskWorkerProtocol._

  private val taskGate: Promise[Unit] = Promise()

  private class GatedTask(gate: Future[Unit]) extends FileGroupTaskStep {
    override def execute: FileGroupTaskStep = {
      Await.result(gate, 5000 millis)
      this
    }
    
    override def cancel: Unit = cancelFn.store()
  }
  

  override protected def startCreatePagesTask(documentSetId: Long, uploadedFileId: Long): FileGroupTaskStep =
    new GatedTask(taskGate.future)

  override protected def startCreateDocumentsTask(documentSetId: Long, splitDocuments: Boolean, progressReporter: ActorRef): FileGroupTaskStep = 
    new GatedTask(taskGate.future)
  
  override protected def deleteFileUploadJob(documentSetId: Long, fileGroupId: Long): Unit = {}

  private def manageTaskGate: PartialFunction[Any, Unit] = {
    case CancelYourself => self ! CancelTask
    case CompleteTaskStep => taskGate.success()
  }

  override def receive = manageTaskGate orElse super.receive
}


