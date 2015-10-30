package com.overviewdocs.jobhandler.filegroup

import akka.actor.{Actor,ActorRef,Props}

import com.overviewdocs.messages.DocumentSetCommands.AddDocumentsFromFileGroup
import com.overviewdocs.models.GroupedFileUpload

/** Asks a broker for work and does the work it's given.
  */
class AddDocumentsWorker(
  val broker: ActorRef,
  val impl: AddDocumentsImpl
) extends Actor {
  import AddDocumentsWorker._

  override def preStart = ready

  override def receive = {
    case HandleUpload(command, upload) => {
      import context.dispatcher
      val broker = sender

      for {
        _ <- impl.processUpload(command, upload)
      } yield {
        broker ! AddDocumentsWorkBroker.WorkerDoneHandleUpload(command)
        ready
      }
    }

    case FinishJob(command) => {
      import context.dispatcher
      val broker = sender

      for {
        _ <- impl.finishJob(command)
      } yield {
        broker ! AddDocumentsWorkBroker.WorkerDoneFinishJob(command)
        ready
      }
    }

    case CancelHandleUpload(command) => {} // TODO speed things up
  }

  private def ready: Unit = broker ! AddDocumentsWorkBroker.WorkerReady
}

object AddDocumentsWorker {
  def props(broker: ActorRef, impl: AddDocumentsImpl): Props = Props(new AddDocumentsWorker(broker, impl))

  /** A message from the broker. */
  sealed trait Work {
    val command: AddDocumentsFromFileGroup
  }

  /** The worker should transform the given GroupedFileUpload into Documents.
    *
    * The worker must send a `WorkerDoneHandleUpload(command)` message when its
    * processing is complete. Otherwise, the command will stall.
    *
    * After that, the worker should send `WorkerReady`.
    */
  case class HandleUpload(override val command: AddDocumentsFromFileGroup, upload: GroupedFileUpload) extends Work

  /** The worker should update the DocumentSet and delete the Job.
    *
    * The worker must send a `WorkerDoneFinishJob(command)` message when its
    * processing is complete. Otherwise, the command will stall.
    *
    * After that, the worker should send `WorkerReady`.
    */
  case class FinishJob(override val command: AddDocumentsFromFileGroup) extends Work

  /** The worker should complete its work ASAP. (Spontaneous suggestion.)
    *
    * This message may be sent to the worker at any time after it has received
    * a HandleUpload() with the given command. The worker may ignore it, or it
    * may skip any of its side-effects (e.g., document creation) and even skip
    * sending the broker WorkerDoneHandleUpload(command).
    *
    * The worker must ignore this message if it is presently working on a
    * HandleUpload() message with the given command.
    *
    * The broker will ignore future WorkerDoneHandleUpload() messages for this
    * command. The worker should eventually send a WorkerReady message to resume
    * its business.
    *
    * Note that even after a CancelHandleUpload, there will be a FinishJob for
    * that same command. That's where the cleanup will occur.
    */
  case class CancelHandleUpload(override val command: AddDocumentsFromFileGroup) extends Work
}
