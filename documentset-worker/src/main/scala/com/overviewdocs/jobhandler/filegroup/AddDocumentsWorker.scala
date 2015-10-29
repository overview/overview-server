package com.overviewdocs.jobhandler.filegroup

import akka.actor.{Actor,ActorRef,Props}

import com.overviewdocs.models.GroupedFileUpload

/** Asks a broker for work and does the work it's given.
  */
class AddDocumentsWorker(
  val broker: ActorRef,
  val impl: AddDocumentsImpl
) extends Actor {
  import AddDocumentsWorker._

  override def preStart = broker ! AddDocumentsWorkBroker.WorkerReady

  override def receive = {
    case HandleUpload(job, upload) => {
      import context.dispatcher
      val broker = sender

      for {
        _ <- impl.processUpload(job, upload)
      } yield {
        broker ! AddDocumentsWorkBroker.WorkerDoneHandleUpload(job)
        broker ! AddDocumentsWorkBroker.WorkerReady
      }
    }

    case FinishJob(job) => {
      import context.dispatcher
      val broker = sender

      for {
        _ <- impl.finishJob(job)
      } yield broker ! AddDocumentsWorkBroker.WorkerReady
    }

    case CancelHandleUpload(job) => {} // TODO speed things up
  }
}

object AddDocumentsWorker {
  def props(broker: ActorRef, impl: AddDocumentsImpl): Props = Props(new AddDocumentsWorker(broker, impl))

  /** A message from the broker. */
  sealed trait Work {
    val job: AddDocumentsJob
  }

  /** The worker should transform the given GroupedFileUpload into Documents.
    *
    * The worker must send a `WorkerDoneHandleUpload(job)` message when its
    * processing is complete. Otherwise, the job will stall.
    */
  case class HandleUpload(override val job: AddDocumentsJob, upload: GroupedFileUpload) extends Work

  /** The worker should update the DocumentSet and delete the Job.
    *
    * The broker does not expect a any particular message in response. When the 
    * job is complete, the worker should eventually send `WorkerReady`.
    */
  case class FinishJob(override val job: AddDocumentsJob) extends Work

  /** The worker should complete its work ASAP. (Spontaneous suggestion.)
    *
    * This message may be sent to the worker at any time after it has received
    * a HandleUpload() with the given job. The worker may ignore it, or it may
    * skip any of its side-effects (e.g., document creation) and even skip
    * sending the broker WorkerDoneHandleUpload(job).
    *
    * The worker must ignore this message if it is presently working on a
    * HandleUpload() message with the given job.
    *
    * The broker will ignore future WorkerDoneHandleUpload() messages for this
    * job. The worker should eventually send a WorkerReady message to resume
    * its business.
    *
    * Note that even after a CancelHandleUpload, there will be a FinishJob for
    * that same job. That's where the cleanup will occur.
    */
  case class CancelHandleUpload(override val job: AddDocumentsJob) extends Work
}
