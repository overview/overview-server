package com.overviewdocs.jobhandler.filegroup

import akka.actor.{Actor,ActorRef,Props}
import scala.collection.mutable
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.messages.DocumentSetCommands.AddDocumentsFromFileGroup
import com.overviewdocs.models.{FileGroup,GroupedFileUpload}

class AddDocumentsWorkBroker(progressReporter: ActorRef) extends Actor {
  private case class JobInfo(
    workGenerator: AddDocumentsWorkGenerator,
    ackTarget: ActorRef,
    ackMessage: Any,
    runningWorkers: mutable.Set[ActorRef] = mutable.Set()
  ) {
    def fileGroup = workGenerator.fileGroup
    def fileGroupId: Long = fileGroup.id
  }

  private val waitingWorkers: mutable.Queue[ActorRef] = mutable.Queue() // round-robin
  private val jobs: mutable.Map[Long,JobInfo] = mutable.Map() // fileGroupId => info
  private val jobsCircle: mutable.Queue[JobInfo] = mutable.Queue() // round-robin

  import AddDocumentsWorkBroker._

  protected def loadWorkGeneratorForCommand(command: AddDocumentsFromFileGroup)(implicit ec: ExecutionContext): Future[AddDocumentsWorkGenerator] = {
    AddDocumentsWorkGenerator.loadForCommand(command)(ec)
  }

  def receive = {
    case DoWorkThenAck(command, ackTarget, ackMessage) => {
      import context.dispatcher
      for {
        workGenerator <- loadWorkGeneratorForCommand(command)
      } yield {
        val jobInfo = JobInfo(workGenerator, ackTarget, ackMessage)
        jobs(jobInfo.fileGroupId) = jobInfo
        jobsCircle.enqueue(jobInfo)
        sendJobs
      }
    }

    case CancelJob(fileGroupId) => {
      // Don't stop any running processes; just tell everybody to skip to the
      // end...
      jobs.get(fileGroupId).map { jobInfo =>
        jobInfo.workGenerator.skipRemainingFileWork
        jobInfo.runningWorkers.foreach { worker =>
          worker ! AddDocumentsWorker.CancelHandleUpload(jobInfo.fileGroup)
        }
      }
    }

    case WorkerReady => {
      waitingWorkers.enqueue(sender)
      sendJobs
    }

    case WorkerDoneHandleUpload(fileGroup, upload) => {
      val jobInfo = jobs(fileGroup.id) // or crash
      jobInfo.workGenerator.markWorkDone(upload)
      jobInfo.runningWorkers.-=(sender)
      reportProgress(jobInfo)
      sendJobs // maybe this message freed up another Work
    }

    case WorkerHandleUploadProgress(fileGroup, upload, fractionComplete) => {
      val jobInfo = jobs(fileGroup.id) // or crash
      jobInfo.workGenerator.markWorkProgress(upload, fractionComplete)
      reportProgress(jobInfo)
    }

    case WorkerDoneFinishJob(fileGroup) => {
      val jobInfo = jobs.remove(fileGroup.id).get // or crash
      jobInfo.runningWorkers.-=(sender)
      assert(jobInfo.runningWorkers.isEmpty)
      jobInfo.ackTarget ! jobInfo.ackMessage
      // no need for sendJobs -- it's impossible another Work became ready
    }
  }

  private def reportProgress(jobInfo: JobInfo): Unit = {
    val progress = jobInfo.workGenerator.progress
    progressReporter ! ProgressReporter.Update(
      jobInfo.fileGroupId,
      progress.nFilesProcessed,
      progress.nBytesProcessed,
      progress.estimatedCompletionTime
    )
  }

  private def sendJobs: Unit = {
    while (waitingWorkers.nonEmpty) {
      nextWork match {
        case Some((jobInfo, work)) => {
          val worker = waitingWorkers.dequeue
          jobInfo.runningWorkers.+=(worker)
          worker ! work
        }
        case None => { return }
      }
    }
  }

  /** Finds a unit of Work from a non-idling generator.
    *
    * Mutates `jobsCircle` to find the first non-idling job in round-robin
    * fashion.
    *
    * If all generators are idling, returns None.
    */
  private def nextWork: Option[(JobInfo,AddDocumentsWorker.Work)] = {
    if (jobsCircle.isEmpty) return None

    val head = jobsCircle.head

    while (true) {
      val job = jobsCircle.dequeue
      job.workGenerator.nextWork match {
        case AddDocumentsWorkGenerator.ProcessFileWork(upload) => {
          jobsCircle.enqueue(job)
          return Some((job, AddDocumentsWorker.HandleUpload(job.fileGroup, upload)))
        }
        case AddDocumentsWorkGenerator.FinishJobWork => {
          // don't re-enqueue the job, but do keep it in `jobs`.
          return Some((job, AddDocumentsWorker.FinishJob(job.fileGroup)))
        }
        case AddDocumentsWorkGenerator.NoWorkForNow => {
          jobsCircle.enqueue(job)
          if (jobsCircle.head == head) return None // We've looped around the entire circle
        }
      }
    }

    throw new AssertionError("Exited an infinite loop")
  }
}

object AddDocumentsWorkBroker {
  def props(progressReporter: ActorRef): Props = Props(new AddDocumentsWorkBroker(progressReporter))

  /** A message from a worker. */
  sealed trait WorkerMessage

  /** The sender is ready to process some Work. */
  case object WorkerReady extends WorkerMessage

  /** The sender completed some previously-returned work.
    *
    * To be absolutely clear: this message does not mean the entire `command` is
    * complete: it merely means one unit of `Work` is complete.
    */
  case class WorkerDoneHandleUpload(fileGroup: FileGroup, upload: GroupedFileUpload) extends WorkerMessage

  /** The sender is partly done processing a file.
    *
    * The broker can use this to coordinate progress reporting.
    *
    * @param fileGroup The FileGroup of this upload.
    * @param upload The Upload the worker is working on.
    * @param fractionComplete A fraction between 0.0 and 1.0, inclusive.
    */
  case class WorkerHandleUploadProgress(
    fileGroup: FileGroup,
    upload: GroupedFileUpload,
    fractionComplete: Double
  ) extends WorkerMessage

  /** The sender completed some previously-returned work.
    */
  case class WorkerDoneFinishJob(fileGroup: FileGroup) extends WorkerMessage

  /** A request from the parent to generate and complete all work, then send
    * `ackMessage` to `receiver`.
    */
  case class DoWorkThenAck(command: AddDocumentsFromFileGroup, receiver: ActorRef, ackMessage: Any)

  /** A request from the parent to cancel a command.
    *
    * Perhaps this is a misnomer. "Cancel" really means "finish as quickly as
    * possible, deleting whatever information is necessary." It will delete
    * unprocessed GroupedFileUploads, and the broker will send workers
    * CancelHandleUpload() messages so they skip to the end of their own
    * processing.
    */
  case class CancelJob(fileGroupId: Long)
}
