package com.overviewdocs.jobhandler.filegroup

import akka.actor.{Actor,ActorRef,Props}

import com.overviewdocs.models.{FileGroup,GroupedFileUpload}

/** Asks a broker for work and does the work it's given.
  */
class AddDocumentsWorker(
  val broker: ActorRef,
  val impl: AddDocumentsImpl
) extends Actor {
  import AddDocumentsWorker._

  override def preStart = ready

  override def receive = {
    case HandleUpload(fileGroup, upload) => {
      import context.dispatcher
      val broker = sender

      for {
        _ <- impl.processUpload(fileGroup, upload)
      } yield {
        broker ! AddDocumentsWorkBroker.WorkerDoneHandleUpload(fileGroup, upload)
        ready
      }
    }

    case FinishJob(fileGroup) => {
      import context.dispatcher
      val broker = sender

      for {
        _ <- impl.finishJob(fileGroup)
      } yield {
        broker ! AddDocumentsWorkBroker.WorkerDoneFinishJob(fileGroup)
        ready
      }
    }

    case CancelHandleUpload(fileGroup) => {} // TODO speed things up
  }

  private def ready: Unit = broker ! AddDocumentsWorkBroker.WorkerReady
}

object AddDocumentsWorker {
  def props(broker: ActorRef, impl: AddDocumentsImpl): Props = Props(new AddDocumentsWorker(broker, impl))

  /** A message from the broker. */
  sealed trait Work { val fileGroup: FileGroup }

  /** The worker should transform the given GroupedFileUpload into Documents.
    *
    * The worker must send a `WorkerDoneHandleUpload(fileGroup)` message when its
    * processing is complete. Otherwise, the fileGroup will stall.
    *
    * After that, the worker should send `WorkerReady`.
    */
  case class HandleUpload(override val fileGroup: FileGroup, upload: GroupedFileUpload) extends Work

  /** The worker should update the DocumentSet and delete the Job.
    *
    * The worker must send a `WorkerDoneFinishJob(fileGroup)` message when its
    * processing is complete. Otherwise, the command will stall.
    *
    * After that, the worker should send `WorkerReady`.
    */
  case class FinishJob(override val fileGroup: FileGroup) extends Work

  /** The worker should complete its work ASAP. (Spontaneous suggestion.)
    *
    * This message may be sent to the worker at any time after it has received
    * a HandleUpload() with the given fileGroup. The worker may ignore it, or
    * it may skip any of its side-effects (e.g., document creation) -- whatever
    * it takes to expedite sending the broker
    * `WorkerDoneHandleUpload(fileGroup, upload)`.
    *
    * The worker must ignore this message if it is not presently working on a
    * HandleUpload() message with the given fileGroup.
    *
    * Note that even after a CancelHandleUpload, there will be a FinishJob for
    * that same command. That's where the cleanup will occur.
    */
  case class CancelHandleUpload(override val fileGroup: FileGroup) extends Work
}
