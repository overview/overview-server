package com.overviewdocs.jobhandler.documentset

import akka.actor.Actor
import akka.actor.FSM
import akka.pattern.pipe
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.{ Failure, Success }

import com.overviewdocs.background.filecleanup.FileRemovalRequestQueueProtocol._
import com.overviewdocs.database.DocumentSetCreationJobDeleter
import com.overviewdocs.database.DocumentSetDeleter
import com.overviewdocs.jobhandler.JobProtocol._
import com.overviewdocs.util.Logger
import com.overviewdocs.searchindex.IndexClient

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
 */
object DeleteHandlerProtocol {
  case class DeleteDocumentSet(documentSetId: Long, waitForJobRemoval: Boolean)
  case class DeleteReclusteringJob(jobId: Long)
}

object DeleteHandlerFSM {
  sealed trait State
  case object Idle extends State
  case object WaitingForRunningJobRemoval extends State
  case object Running extends State

  sealed trait Data
  case object NoData extends Data
  case class DeleteTarget(documentSetId: Long) extends Data
  case class DeleteTreeTarget(jobId: Long) extends Data
  case class RetryAttempts(documentSetId: Long, n: Int) extends Data
}

import DeleteHandlerFSM._

trait DeleteHandler extends Actor with FSM[State, Data] {
  import DeleteHandlerProtocol._
  import context.dispatcher

  protected val logger = Logger.forClass(getClass)

  val searchIndexClient: IndexClient
  val documentSetDeleter: DocumentSetDeleter
  val jobDeleter: DocumentSetCreationJobDeleter
  val jobStatusChecker: JobStatusChecker

  val RetryTimer = "retry"

  protected val JobWaitDelay = 100 milliseconds
  protected val MaxRetryAttempts = 600

  protected val fileRemovalQueuePath: String
  private lazy val fileRemovalQueue = context.actorSelection(fileRemovalQueuePath) 
  
  private object Message {
    case object RetryDelete
    case object DeleteComplete
    case object DeleteReclusteringJobComplete
    case class DeleteFailed(error: Throwable)
    case class JobDeletionTimedOut(documentSetId: Long)
  }

  startWith(Idle, NoData)

  // FIXME: The waitForJobRemoval parameter of DeleteDocumentSet is now redundant, since
  // we need to check for and cancel jobs whenever we delete document sets
  when(Idle) {
    case Event(DeleteDocumentSet(documentSetId, true), _) => {
      if (jobStatusChecker.isJobRunning(documentSetId)) {
        setTimer(RetryTimer, Message.RetryDelete, JobWaitDelay, true)
        goto(WaitingForRunningJobRemoval) using (RetryAttempts(documentSetId, 1))
      } else {
        goto(Running) using (DeleteTarget(documentSetId))
      }
    }
    case Event(DeleteDocumentSet(documentSetId, false), _) => 
      goto(Running) using (DeleteTarget(documentSetId))

    case Event(DeleteReclusteringJob(jobId), _) =>
      goto(Running) using (DeleteTreeTarget(jobId))
    
  }

  when(WaitingForRunningJobRemoval) {
    case Event(Message.RetryDelete, RetryAttempts(documentSetId, n)) => {
      val jobIsRunning = jobStatusChecker.isJobRunning(documentSetId)

      if (jobIsRunning && n >= MaxRetryAttempts) goto(Running)
      else if (jobIsRunning) stay using (RetryAttempts(documentSetId, n + 1))
      else goto(Running) using (DeleteTarget(documentSetId))
    }
  }

  when(Running) {
    case Event(Message.DeleteComplete, DeleteTarget(documentSetId)) => {
      fileRemovalQueue ! RemoveFiles
      context.parent ! JobDone(documentSetId)
      stop
    }
    case Event(Message.DeleteReclusteringJobComplete, DeleteTreeTarget(jobId)) => {
      context.parent ! JobDone(jobId)
      stop
    }
    case Event(Message.DeleteFailed(t), DeleteTarget(documentSetId)) => {
      logger.warn("Deleting indexed documents failed for DocumentSet {}", documentSetId, t)
      context.parent ! JobDone(documentSetId)
      stop
    }
    case Event(Message.JobDeletionTimedOut(documentSetId), _) => {
      logger.warn("Job deletion timed out for DocumentSet {}", documentSetId)
      context.parent ! JobDone(documentSetId)
      stop
    }
  }

  onTransition {
    case _ -> Running =>
      cancelTimer(RetryTimer)
      nextStateData match {
        case DeleteTarget(documentSetId) => deleteDocumentSet(documentSetId)
        case DeleteTreeTarget(jobId) => deleteReclusteringJob(jobId)
        case RetryAttempts(documentSetId, n) => self ! Message.JobDeletionTimedOut(documentSetId)
        case _ =>
      }
  }

  private def deleteDocumentSet(documentSetId: Long): Unit = {
    val deleteJobThenDocumentSet = jobDeleter.deleteByDocumentSet(documentSetId)
      .flatMap(_ => documentSetDeleter.delete(documentSetId))

    val deleteIndex = searchIndexClient.removeDocumentSet(documentSetId)

    val result = for {
      dsResult <- deleteJobThenDocumentSet
      siResult <- deleteIndex
    } yield Message.DeleteComplete

    result.recover {
      case t: Throwable => Message.DeleteFailed(t)
    } pipeTo self
  }

  private def deleteReclusteringJob(jobId: Long): Unit = {
    val deletion = jobDeleter.delete(jobId)

    deletion.map(_ => Message.DeleteReclusteringJobComplete) pipeTo self

  }
}
