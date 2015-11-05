package com.overviewdocs.jobhandler.documentset

import akka.actor.{Actor,ActorRef,Props}
import scala.concurrent.{ExecutionContext,Future}
import scala.util.{Failure,Success}

import com.overviewdocs.database.DocumentSetDeleter
import com.overviewdocs.jobhandler.filegroup.AddDocumentsWorkBroker
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
  val addDocumentsWorkBroker: ActorRef,
  val documentSetDeleter: DocumentSetDeleter
) extends Actor
{
  import DocumentSetCommands._

  private val logger = Logger.forClass(getClass)

  override def preStart = sendReady

  override def receive = {
    case command: Command => run(command)(context.dispatcher)
  }

  private def run(command: Command)(implicit ec: ExecutionContext): Unit = {
    logger.info("Handling DocumentSet command: {}", command)

    command match {
      case addDocuments: AddDocumentsFromFileGroup => {
        // AddDocuments is a special case, because it gets its own scheduler.
        // The DocumentSetCommandWorker will return right away; that way, the
        // downstream AddDocumentsWorkBroker can juggle all import jobs at
        // once, while this DocumentSetCommandWorker can work on other
        // commands.
        val message = AddDocumentsWorkBroker.DoWorkThenAck(addDocuments, broker, done(addDocuments.documentSetId))
        addDocumentsWorkBroker ! message
        sendReady
      }
      case CancelAddDocumentsFromFileGroup(documentSetId, fileGroupId) => {
        // Another special case: this message arrives spontaneously. The
        // DocumentSetCommandWorker should not respond to this message;
        // instead, it should pass it to the AddDocumentsWorkBroker and keep
        // going as it always has.
        addDocumentsWorkBroker ! AddDocumentsWorkBroker.CancelJob(fileGroupId)
        // Don't send "ready": we don't know or care whether this worker has
        // actually been doing anything.
      }
      case DeleteDocumentSet(documentSetId) => {
        documentSetDeleter.delete(documentSetId).onComplete {
          case Success(()) => {
            sendDone(documentSetId)
            sendReady
          }
          case Failure(ex) => self ! ex
        }
      }
      case CancelJob(documentSetId, jobId) => {
        // no-op ... for now!
      }
    }
  }

  private def done(documentSetId: Long) = DocumentSetMessageBroker.WorkerDoneDocumentSetCommand(documentSetId)

  private def sendDone(documentSetId: Long): Unit = broker ! done(documentSetId)

  private def sendReady: Unit = broker ! DocumentSetMessageBroker.WorkerReady
}

object DocumentSetCommandWorker {
  def props(broker: ActorRef, addDocumentsWorkBroker: ActorRef, documentSetDeleter: DocumentSetDeleter): Props = {
    Props(new DocumentSetCommandWorker(broker, addDocumentsWorkBroker, documentSetDeleter))
  }
}
