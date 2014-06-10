package org.overviewproject.jobhandler.filegroup

import scala.language.postfixOps
import scala.concurrent.duration._
import scala.concurrent.Promise
import akka.actor.ActorRef
import akka.actor.Actor
import scala.concurrent.Future
import scala.concurrent.Await
import akka.agent.Agent
import org.overviewproject.test.ParameterStore

object GatedTaskWorkerProtocol {
  case object CancelYourself
  case object CompleteTaskStep
}

class GatedTaskWorker(override protected val jobQueuePath: String, cancelFn: ParameterStore[Unit]) extends FileGroupTaskWorker {

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
  

  override protected def startCreatePagesTask(documentSetId: Long, fileGroupId: Long, uploadedFileId: Long): FileGroupTaskStep =
    new GatedTask(taskGate.future)

  override protected def deleteFileUploadJob(documentSetId: Long, fileGroupId: Long): Unit = {}

  private def manageTaskGate: PartialFunction[Any, Unit] = {
    case CancelYourself => self ! CancelTask
    case CompleteTaskStep => taskGate.success()
  }

  override def receive = manageTaskGate orElse super.receive
}


