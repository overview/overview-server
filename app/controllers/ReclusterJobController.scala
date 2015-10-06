package controllers

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningJob
import controllers.util.JobQueueSender
import models.orm.finders.DocumentSetCreationJobFinder
import models.orm.stores.DocumentSetCreationJobStore
import com.overviewdocs.messages.DocumentSetCommands
import com.overviewdocs.tree.orm.{DocumentSetCreationJob,DocumentSetCreationJobState}

trait ReclusterJobController extends Controller {
  trait Storage {
    /** Cancels a job, and returns the state of the job at this time.
      *
      * Note how we avoid a race condition: we SELECT FOR UPDATE. That
      * guarantees that the worker cannot _start_ a job if this method
      * returns JobWasNotRunning.
      *
      * There's another race we don't care about. If this method returns
      * JobWasRunning, the job might have completed before it returns.
      */
    def cancelJob(id: Long) : ReclusterJobController.CancelResult
  }

  val storage : ReclusterJobController.Storage
  val jobQueue: JobQueueSender

  def delete(jobId: Long) = AuthorizedAction.inTransaction(userOwningJob(jobId)) {
    val cancelResult = storage.cancelJob(jobId)
    cancelResult match {
      case ReclusterJobController.JobWasNotRunning => jobQueue.send(DocumentSetCommands.DeleteDocumentSetJob(-1L, jobId)) // -1L because I think this'll work for now ... and really we want all this code gone.
      case ReclusterJobController.JobWasRunning => // The worker will delete it or finish it -- we don't care which
      case ReclusterJobController.JobNotFound => // Yay, it finished before we could cancel it
    }
    NoContent
  }
}

object ReclusterJobController extends ReclusterJobController {
  sealed trait CancelResult
  case object JobWasNotRunning extends CancelResult
  case object JobWasRunning extends CancelResult
  case object JobNotFound extends CancelResult

  object DatabaseStorage extends Storage {
    override def cancelJob(id: Long) = {
      val maybeJob = DocumentSetCreationJobFinder.byDocumentSetCreationJob(id).forUpdate.headOption
      maybeJob match {
        case Some(job) =>
          DocumentSetCreationJobStore.insertOrUpdate(job.copy(state=DocumentSetCreationJobState.Cancelled))
          job.state match {
            case DocumentSetCreationJobState.InProgress => JobWasRunning
            case _ => JobWasNotRunning
          }
        case None => JobNotFound
      }
    }
  }

  override val storage = DatabaseStorage
  override val jobQueue = JobQueueSender
}
