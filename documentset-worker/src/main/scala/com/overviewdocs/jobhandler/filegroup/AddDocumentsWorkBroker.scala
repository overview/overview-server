package com.overviewdocs.jobhandler.filegroup

import akka.actor.{Actor,ActorRef,Props}
import scala.collection.mutable

import com.overviewdocs.models.GroupedFileUpload

class AddDocumentsWorkBroker() extends Actor {
  private val waitingWorkers: mutable.Queue[ActorRef] = mutable.Queue()
  private val runningWorkers: mutable.Map[Long,mutable.Set[ActorRef]] = mutable.Map() // jobId => actors
  private val generatorsCircle: mutable.Queue[AddDocumentsWorkGenerator] = mutable.Queue()
  private val generators: mutable.Map[Long,AddDocumentsWorkGenerator] = mutable.Map() // jobId => generator

  import AddDocumentsWorkBroker._

  def receive = {
    case AddWorkGenerator(workGenerator) => {
      generatorsCircle.enqueue(workGenerator)
      generators.+=(workGenerator.job.documentSetCreationJobId -> workGenerator)
      sendJobs
    }

    case CancelJob(documentSetCreationJobId) => {
      generators.get(documentSetCreationJobId).map { generator =>
        generator.skipRemainingFileWork
        runningWorkers.getOrElse(documentSetCreationJobId, Seq()).foreach { worker =>
          worker ! AddDocumentsWorker.CancelHandleUpload(generator.job)
        }
      }
    }

    case WorkerReady => {
      waitingWorkers.enqueue(sender)
      sendJobs
    }

    case WorkerDoneHandleUpload(job) => {
      generators(job.documentSetCreationJobId).markDoneOne
      // The worker can send that a job is done even after cancellation
      runningWorkers.get(job.documentSetCreationJobId).map(_.-=(sender))
      sendJobs
    }
  }

  private def sendJobs: Unit = {
    while (waitingWorkers.nonEmpty) {
      nextWork match {
        case Some(message) => {
          val worker = waitingWorkers.dequeue
          val jobId = message.job.documentSetCreationJobId
          runningWorkers.getOrElseUpdate(jobId, mutable.Set()).+=(worker)
          worker ! message
        }
        case None => { return }
      }
    }
  }

  /** Finds a unit of Work from a non-idling generator.
    *
    * Mutates `generatorsCircle` to find the first non-idling generator in
    * round-robin fashion.
    *
    * If all generators are idling, returns None.
    */
  private def nextWork: Option[AddDocumentsWorker.Work] = {
    if (generatorsCircle.isEmpty) return None

    val head = generatorsCircle.head

    while (true) {
      val generator = generatorsCircle.dequeue
      generator.nextWork match {
        case AddDocumentsWorkGenerator.ProcessFileWork(upload) => {
          generatorsCircle.enqueue(generator)
          return Some(AddDocumentsWorker.HandleUpload(generator.job, upload))
        }
        case AddDocumentsWorkGenerator.FinishJobWork => {
          // Forget about the generator; don't re-enqueue it
          val jobId = generator.job.documentSetCreationJobId
          runningWorkers.remove(jobId)
          generators.remove(jobId)
          return Some(AddDocumentsWorker.FinishJob(generator.job))
        }
        case AddDocumentsWorkGenerator.NoWorkForNow => {
          generatorsCircle.enqueue(generator)
          if (generatorsCircle.head == head) return None // We've looped around the entire circle
        }
      }
    }

    throw new AssertionError("Exited an infinite loop")
  }
}

object AddDocumentsWorkBroker {
  def props: Props = Props[AddDocumentsWorkBroker]()

  /** A message from a worker. */
  sealed trait WorkerMessage

  /** The sender is ready to process some Work. */
  case object WorkerReady extends WorkerMessage

  /** The sender completed some previously-returned work.
    *
    * To be absolutely clear: this message does not mean the entire `job` is
    * complete: it merely means one unit of `Work` is complete.
    *
    * @param job The job the work pertained to.
    */
  case class WorkerDoneHandleUpload(job: AddDocumentsJob) extends WorkerMessage

  /** A message from elsewhere. */
  case class AddWorkGenerator(workGenerator: AddDocumentsWorkGenerator)

  /** A request to cancel a job.
    *
    * Perhaps this is a misnomer. "Cancel" really means "finish as quickly as
    * possible, deleting whatever information is necessary." It will delete
    * unprocessed GroupedFileUploads, and the broker will send workers
    * CancelHandleUpload() messages so they skip to the end of their own
    * processing.
    */
  case class CancelJob(documentSetCreationJobId: Long)
}
