package com.overviewdocs.jobhandler.documentset

import akka.actor.{Actor,ActorRef,Props}
import scala.concurrent.{ExecutionContext,Future}
import scala.util.{Failure,Success}

import com.overviewdocs.akkautil.BrokerActor
import com.overviewdocs.background.reindex.ReindexActor
import com.overviewdocs.clone.Cloner
import com.overviewdocs.database.DocumentSetDeleter
import com.overviewdocs.jobhandler.csv.CsvImportWorkBroker
import com.overviewdocs.jobhandler.documentcloud.DocumentCloudImportWorkBroker
//import com.overviewdocs.jobhandler.filegroup.AddDocumentsWorkBroker
import com.overviewdocs.searchindex.Indexer
import com.overviewdocs.messages.{DocumentSetReadCommands,DocumentSetCommands}
import com.overviewdocs.util.Logger

/** Processes DocumentSetCommands and DocumentSetReadCommands.
  *
  * FIXME we really want reader-writer locks. The best approach is probably to
  * use one Actor per open DocumentSet. In the meantime, we serialize
  * DocumentSetCommands and just run DocumentSetReadCommands as soon as we
  * receive them.
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
  //val addDocumentsWorkBroker: ActorRef,
  val csvImportWorkBroker: ActorRef,
  val documentCloudImportWorkBroker: ActorRef,
  val indexer: ActorRef,
  val reindexer: ActorRef,
  val sortBroker: ActorRef,
  val cloner: Cloner,
  val documentSetDeleter: DocumentSetDeleter
) extends Actor
{
  import DocumentSetCommands._
  import DocumentSetReadCommands.ReadCommand

  private val logger = Logger.forClass(getClass)

  override def preStart = sendReady

  override def receive = {
    case DocumentSetMessageBroker.ReadCommand(command, replyTo) => runRead(command, replyTo)
    case command: Command => run(command)(context.dispatcher)
  }

  private def runRead(command: ReadCommand, replyTo: ActorRef): Unit = {
    logger.info("Handling DocumentSetRead command {}, will reply to {}", command, replyTo)

    // FIXME use reader/writer locks, per DocumentSet. Probably one Actor per
    // DocumentSet, Stashing messages.
    //
    // In the meantime, There Are Ways to cause terrible performance.

    indexer.tell(command, replyTo)
    sendDone(command.documentSetId)
    sendReady
  }

  private def run(command: Command)(implicit ec: ExecutionContext): Unit = {
    logger.info("Handling DocumentSet command: {}", command)

    command match {
      case addDocuments: AddDocumentsFromCsvImport => {
        // AddDocuments is a special case, because it gets its own scheduler.
        // The DocumentSetCommandWorker will return right away; that way, the
        // downstream CsvImportWorkBroker can juggle all import jobs at once,
        // while this DocumentSetCommandWorker can work on other commands.
        val message = CsvImportWorkBroker.DoWorkThenAck(addDocuments, broker, done(addDocuments.documentSetId))
        csvImportWorkBroker ! message
        sendReady
      }
      case addDocuments: AddDocumentsFromDocumentCloud => {
        // AddDocuments is a special case, because it gets its own scheduler.
        // The DocumentSetCommandWorker will return right away; that way, the
        // downstream DocumentCloudWorkBroker can juggle all import jobs at
        // once, while this DocumentSetCommandWorker can work on other commands.
        val message = DocumentCloudImportWorkBroker.DoWorkThenAck(addDocuments, broker, done(addDocuments.documentSetId))
        documentCloudImportWorkBroker ! message
        sendReady
      }
      case addDocuments: AddDocumentsFromFileGroup => {
        // AddDocuments is a special case, because it gets its own scheduler.
        // The DocumentSetCommandWorker will return right away; that way, the
        // downstream AddDocumentsWorkBroker can juggle all import jobs at
        // once, while this DocumentSetCommandWorker can work on other
        // commands.
        //val message = AddDocumentsWorkBroker.DoWorkThenAck(addDocuments, broker, done(addDocuments.documentSetId))
        //addDocumentsWorkBroker ! message // TODO implement this!
        sendReady
      }
      case command: SortField => {
        // SortField is a special case, because it gets its own scheduler.
        // The DocumentSetCommandWorker will return right away; that way, the
        // downstream AddDocumentsWorkBroker can juggle all import+sort jobs at
        // once, while this DocumentSetCommandWorker can work on other
        // commands.
        sortBroker ! BrokerActor.Work(command, sender)
        sendDone(command.documentSetId)
        sendReady
      }
      case command: Reindex => {
        logger.info("Received Reindex command: {}", command)
        reindexer ! ReindexActor.ReindexNextDocumentSet
        // We reindex asynchronously and show warnings when the index isn't
        // up to date.
        sendDone(command.documentSetId)
        sendReady
      }
      case command: ReindexDocument => {
        val message = Indexer.DoWorkThenAck(command, broker, done(command.documentSetId))
        indexer ! message
        sendReady
      }
      case CancelAddDocumentsFromFileGroup(documentSetId, fileGroupId) => {
        // Another special case: this message arrives spontaneously. The
        // DocumentSetCommandWorker should not respond to this message;
        // instead, it should pass it to the AddDocumentsWorkBroker and keep
        // going as it always has.
        //addDocumentsWorkBroker ! AddDocumentsWorkBroker.CancelJob(fileGroupId) // TODO handle this
        // Don't send "ready": we don't know or care whether this worker has
        // actually been doing anything.
      }
      case command: CloneDocumentSet => {
        cloner.run(command.cloneJob).onComplete {
          case Success(()) => {
            sendDone(command.documentSetId)
            sendReady
          }
          case Failure(ex) => self ! ex
        }
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
    }
  }

  private def done(documentSetId: Long) = DocumentSetMessageBroker.WorkerDoneDocumentSetCommand(documentSetId)

  private def sendDone(documentSetId: Long): Unit = broker ! done(documentSetId)

  private def sendReady: Unit = {
    logger.info("Done with this command")
    broker ! DocumentSetMessageBroker.WorkerReady
  }
}

object DocumentSetCommandWorker {
  def props(
    broker: ActorRef,
    //addDocumentsWorkBroker: ActorRef,
    csvImportWorkBroker: ActorRef, 
    documentCloudImportWorkBroker: ActorRef,
    indexer: ActorRef,
    reindexer: ActorRef,
    sortBroker: ActorRef,
    cloner: Cloner,
    documentSetDeleter: DocumentSetDeleter
  ): Props = {
    Props(new DocumentSetCommandWorker(
      broker,
      //addDocumentsWorkBroker,
      csvImportWorkBroker,
      documentCloudImportWorkBroker,
      indexer,
      reindexer,
      sortBroker,
      cloner,
      documentSetDeleter
    ))
  }
}
