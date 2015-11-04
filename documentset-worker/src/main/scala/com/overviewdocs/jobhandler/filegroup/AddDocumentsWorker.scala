package com.overviewdocs.jobhandler.filegroup

import akka.actor.{Actor,ActorRef,Props,Status}
import scala.concurrent.{ExecutionContext,Future}
import scala.util.{Failure,Success}

import com.overviewdocs.models.{FileGroup,GroupedFileUpload}
import com.overviewdocs.util.Logger

/** Asks a broker for work and does the work it's given.
  *
  * By agreement between the broker and this worker, each worker only does
  * one thing at a time.
  */
class AddDocumentsWorker(
  val broker: ActorRef,
  val impl: AddDocumentsImpl
) extends Actor {
  private val logger = Logger.forClass(getClass)

  import AddDocumentsWorker._
  import context.dispatcher

  @volatile var cancelling: Boolean = false

  override def preStart = ready

  override def receive = {
    case HandleUpload(fileGroup, upload) => {
      cancelling = false

      startWork(impl.processUpload(fileGroup, upload, onProgress(fileGroup, upload, _))) {
        broker ! AddDocumentsWorkBroker.WorkerDoneHandleUpload(fileGroup, upload)
        ready
      }
    }

    case FinishJob(fileGroup) => {
      startWork(impl.finishJob(fileGroup)) {
        broker ! AddDocumentsWorkBroker.WorkerDoneFinishJob(fileGroup)
        ready
      }
    }

    case CancelHandleUpload(fileGroup) => {
      /*
       * There's a race here, but it isn't a big deal.
       *
       * Here's the race, at its full extent:
       *
       * 1. Worker sends `WorkerDoneHandleUpload(job1)`
       * 2. Broker sends `CancelHandleUpload(job1)`
       *
       * We know that messages from one actor to another are serialized. That's
       * enough to ensure it can't send us a `CancelHandleUpload` for a job it
       * doesn't think we have. For instance, this is *impossible*:
       *
       * 1. Worker sends `WorkerDoneHandleUpload(job1)`
       * 2. Broker sends `HandleUpload(job2)`
       * 3. Broker sends `CancelHandleUpload(job1)`
       *
       * Conclusion: reset `cancelling` flag when we receive a
       * `CancelHandleUpload`, and we'll be fine.
       */
      cancelling = true
    }
  }

  private def ready: Unit = broker ! AddDocumentsWorkBroker.WorkerReady

  private def startWork(work: => Future[Unit])(after: => Unit): Unit = {
    work.onComplete {
      case Success(()) => after
      case Failure(ex) => broker ! Status.Failure(ex)
    }
  }

  private def onProgress(fileGroup: FileGroup, upload: GroupedFileUpload, fractionComplete: Double): Boolean = {
    broker ! AddDocumentsWorkBroker.WorkerHandleUploadProgress(fileGroup, upload, fractionComplete)
    !cancelling
  }
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
