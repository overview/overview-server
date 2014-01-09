package org.overviewproject.jobhandler.documentset

import scala.language.postfixOps
import scala.concurrent.duration._
import scala.util.{ Failure, Success }

import akka.actor.Actor

import org.overviewproject.jobhandler.JobProtocol._
import org.overviewproject.util.Logger

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

trait DeleteHandler extends Actor with SearcherComponents {
  import DeleteHandlerProtocol._
  import context.dispatcher

  val documentSetDeleter: DocumentSetDeleter
  val jobStatusChecker: JobStatusChecker

  val JobWaitDelay = 100 milliseconds

  def receive = {
    case DeleteDocumentSet(documentSetId) => {

      if (jobStatusChecker.isJobRunning(documentSetId)) context.system.scheduler.scheduleOnce(JobWaitDelay) {
        self ! DeleteDocumentSet(documentSetId)
      }
      else {
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
          case Success(r) => {
            context.parent ! JobDone(documentSetId)
            context.stop(self)
          }
          case Failure(t) => {
            Logger.error("Deleting indexed documents failed", t)
            context.parent ! JobDone(documentSetId)
            context.stop(self)
          }
        }
      }
    }
  }
}