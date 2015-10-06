package com.overviewdocs.jobhandler.documentset

import akka.actor.{Actor,ActorRef}
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.database.{DocumentSetDeleter,DocumentSetCreationJobDeleter}
import com.overviewdocs.messages.DocumentSetCommands
import com.overviewdocs.util.Logger

/** Processes DocumentSetCommands, one at a time.
  *
  * The message-passing world looks like this:
  *
  * 1. DocumentSetCommandWorker starts up and sends `WorkerReady` to `broker`.
  * 2. When there is a `DocumentSetCommands.Command` available, `broker` sends
  *    it to this worker.
  * 3. When this worker finishes, it sends `WorkerReady` to `broker`.
  */
class DocumentSetCommandWorker(
  val broker: ActorRef,
  val documentSetDeleter: DocumentSetDeleter,
  val documentSetCreationJobDeleter: DocumentSetCreationJobDeleter
) extends Actor
{
  private val logger = Logger.forClass(getClass)

  override def preStart = ready

  override def receive = {
    case command: DocumentSetCommands.Command => run(command)(context.dispatcher)
  }

  private def run(command: DocumentSetCommands.Command)(implicit ec: ExecutionContext): Unit = {
    logger.info("Handling DocumentSet command: {}", command)
    commandToFuture(command).onComplete { _ => ready }
  }

  private def commandToFuture(command: DocumentSetCommands.Command)(implicit ec: ExecutionContext): Future[Unit] = command match {
    case DocumentSetCommands.DeleteDocumentSet(documentSetId) => {
      documentSetDeleter.delete(documentSetId)
    }
    case DocumentSetCommands.DeleteDocumentSetJob(documentSetId, jobId) => {
      documentSetCreationJobDeleter.delete(jobId)
    }
  }

  private def ready: Unit = {
      broker ! DocumentSetMessageBroker.WorkerReady
  }
}
