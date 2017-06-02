package com.overviewdocs.searchindex

import akka.actor.{Actor,ActorRef,Props}
import scala.concurrent.Future
import scala.util.{Success,Failure}

import com.overviewdocs.database.DocumentFinder
import com.overviewdocs.messages.{DocumentSetReadCommands,DocumentSetCommands}
import com.overviewdocs.util.Logger

/** Indexes and searches Documents.
  *
  * Maybe we should turn this into one Actor per document set; right now it's a
  * single, big one.
  *
  * There's no queueing in these actors, but that doesn't mean it's safe to
  * index the same document concurrently. Callers are responsible for
  * serializing writes to the same DocumentSet. (Concurrent reads are okay ...
  * for now.)
  */
class Indexer(
  documentFinder: DocumentFinder
) extends Actor {
  import DocumentSetCommands.ReindexDocument
  import DocumentSetReadCommands.{Search,Highlight,Highlights}

  import context.dispatcher

  private val indexClient = LuceneIndexClient.onDiskSingleton

  private val logger = Logger.forClass(getClass)

  override def receive = {
    case Search(documentSetId, query) => {
      respond(sender, indexClient.searchForIds(documentSetId, query))
    }
    case Highlight(documentSetId, documentId, query) => {
      respond(sender, indexClient.highlight(documentSetId, documentId, query))
    }
    case Highlights(documentSetId, documentIds, query) => {
      respond(sender, indexClient.highlights(documentSetId, documentIds, query))
    }
    case Indexer.DoWorkThenAck(ReindexDocument(documentSetId, documentId), sender, ack) => {
      val future = documentFinder.findDocument(documentSetId, documentId)
        .flatMap(maybeDocument => indexClient.addDocuments(documentSetId, maybeDocument.toSeq))
        .onComplete {
          case Success(()) => sender ! ack
          case Failure(ex) => self ! ex
        }
    }
  }

  private def respond[A](sender: ActorRef, future: Future[A]): Unit = {
    future.onComplete {
      case Success(result) => sender ! result
      case Failure(ex) => self ! ex
    }
  }
}

object Indexer {
  def props: Props = {
    Props(new Indexer(DocumentFinder))
  }

  /** Do the work in `command`, then send `ackMessage` to `receiver`. */
  case class DoWorkThenAck(command: DocumentSetCommands.Command, receiver: ActorRef, ackMessage: Any)
}
