package org.overviewproject.jobhandler.documentset

import scala.language.postfixOps
import scala.concurrent.duration._
import scala.util.{ Failure, Success }
import akka.actor.Actor
import org.overviewproject.jobhandler.JobProtocol._
import org.overviewproject.util.Logger
import DeleteHandlerFSM._
import akka.actor.FSM

/**
 * [[DeleteHandler]] deletes a document set and all associated data if deletion is requested after
 * clustering begins.
 * Since the clustering worker is currently completely independent, we can't directly control the
 * status of the clustering process. We want to assume that no more data is being added to the
 * document set while it is deleted, so we want to wait until the clustering job has stopped.
 * The initial, hacky way to accomplish this goal:
 *   # The server sets the document set creation job state to `CANCELLED`
 *   # The clustering worker will delete the document set creation job, when it detects cancellation
 *   # The DeleteHandler will wait until the job has been deleted before deleting the document set
 *   # The DeleteHandler will fail if the job does not get deleted after a given timeout interval
 * Once we unify all worker processes, we can use a more rigorous approach.
 *
 * We also assume that the server will have sent cancellation notices to any jobs trying to clone the
 * document set being deleted. Clone jobs will notice that they're being cancelled before they notice
 * that the source has been deleted.
 *
 * Data to be deleted:
 *   * documents in search index and search index alias
 *   * Nodes created by clustering
 *   * LogEntries
 *   * Tags
 *   * Uploaded documents
 *   * Documents
 *   * DocumentProcessingErrors
 *   * SearchResults
 */
object DeleteHandlerProtocol {
  case class DeleteDocumentSet(documentSetId: Long)
}

object DeleteHandlerFSM {
  sealed trait State
  case object Idle extends State
  case object WaitingForRunningJobRemoval extends State
  case object Running extends State

  sealed trait Data
  case object NoData extends Data
  case class RetryAttempts(documentSetId: Long, n: Int) extends Data
}

trait DeleteHandler extends Actor with FSM[State, Data] with SearcherComponents {
  import DeleteHandlerProtocol._
  import context.dispatcher

  val documentSetDeleter: DocumentSetDeleter
  val jobStatusChecker: JobStatusChecker

  val RetryTimer = "retry"

  protected val JobWaitDelay = 100 milliseconds
  protected val MaxRetryAttempts = 600

  private object Message {
    case object RetryDelete
    case class DeleteComplete(documentSetId: Long)
    case class SearchIndexDeleteFailed(documentSetId: Long, error: Throwable)
    case class DeleteFailed(documentSetId: Long)
  }

  startWith(Idle, NoData)

  when(Idle) {
    case Event(DeleteDocumentSet(documentSetId), _) => {
      if (jobStatusChecker.isJobRunning(documentSetId)) {
        setTimer(RetryTimer, Message.RetryDelete, JobWaitDelay, true)
        goto(WaitingForRunningJobRemoval) using (RetryAttempts(documentSetId, 1))
      } else {
        deleteDocumentSet(documentSetId)
        goto(Running) using (NoData)
      }
    }
  }

  when(WaitingForRunningJobRemoval) {
    case Event(Message.RetryDelete, RetryAttempts(documentSetId, n)) => {
      if (jobStatusChecker.isJobRunning(documentSetId)) {
        if (n >= MaxRetryAttempts) {
          self ! Message.DeleteFailed(documentSetId)
          goto(Running) using (NoData)
        } else stay using (RetryAttempts(documentSetId, n + 1))
      } else {
        deleteDocumentSet(documentSetId)
        goto(Running) using (NoData)
      }
    }
  }

  when(Running) {
    case Event(Message.DeleteComplete(documentSetId), _) => {
      context.parent ! JobDone(documentSetId)
      stop
    }
    case Event(Message.SearchIndexDeleteFailed(documentSetId, t), _) => {
      Logger.error(s"Deleting indexed documents failed for $documentSetId", t)
      context.parent ! JobDone(documentSetId)
      stop
    }
    case Event(Message.DeleteFailed(documentSetId), _) => {
      Logger.error(s"Delete timed out waiting for job to cancel $documentSetId")
      context.parent ! JobDone(documentSetId)
      stop
    }
  }

  onTransition {
    case WaitingForRunningJobRemoval -> _ => cancelTimer(RetryTimer)
  }

  private def deleteDocumentSet(documentSetId: Long): Unit = {
    documentSetDeleter.deleteJobInformation(documentSetId)
    documentSetDeleter.deleteClientGeneratedInformation(documentSetId)
    documentSetDeleter.deleteClusteringGeneratedInformation(documentSetId)
    documentSetDeleter.deleteDocumentSet(documentSetId)

    // delete alias first, so no new documents can be inserted.
    // creating futures inside for comprehension ensures the calls
    // are run sequentially
    val combinedResponse = for {
      aliasResponse <- searchIndex.deleteDocumentSetAlias(documentSetId)
      documentsResponse <- searchIndex.deleteDocuments(documentSetId)
    } yield documentsResponse

    combinedResponse onComplete {
      case Success(r) => self ! Message.DeleteComplete(documentSetId)
      case Failure(t) => self ! Message.SearchIndexDeleteFailed(documentSetId, t)
    }

  }
}
