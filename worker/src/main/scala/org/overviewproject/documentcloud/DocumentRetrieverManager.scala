package org.overviewproject.documentcloud

import scala.concurrent.Promise

import org.overviewproject.documentcloud.DocumentReceiverProtocol._
import org.overviewproject.documentcloud.DocumentRetrieverProtocol._
import org.overviewproject.util.Textify

import akka.actor._


object DocumentRetrieverManagerProtocol {
  // incoming
  case class Retrieve(searchResult: SearchResult)
  // outgoing
  /**
   *  This message may be a place to return progress info.
   * @param totalDocuments is the total number of documents returned by the query
   */
  case class GetSearchResultPage(n: Int)
  case class DocumentRetrieved(n: Int, totalDocuments: Int)
}

trait RetrievalResult {
  val failedRetrievals: Seq[DocumentRetrievalError]
  val numberOfDocumentsRetrieved: Int
  val totalDocumentsInQuery: Int
}

class DocumentRetrieverManager(
  retrieverGenerator: RetrieverGenerator,
  processDocument: (Document, String) => Unit,
  retrievalResult: Promise[RetrievalResult],
  maxDocuments: Int) extends Actor {
  import DocumentRetrieverManagerProtocol._
  import DocumentRetrieverProtocol._

  private val ReceiverActorName: String = "receiver"

  private var requestsCompleted: Int = 0

  def receive = {
    case Retrieve(searchResult) if (searchResult.total == 0) => endRetrieval
    case Retrieve(searchResult) => processSearchResult(searchResult)
    case JobComplete() => updateProgress
  }

  private def processSearchResult(result: SearchResult): Unit = {
    retrieverGenerator.createRetrievers(result, findOrCreateDocumentReceiver())
    if (retrieverGenerator.morePagesAvailable) context.parent ! GetSearchResultPage(result.page + 1)
  }

  private def findOrCreateDocumentReceiver(): akka.actor.ActorRef = {
    context.actorFor(ReceiverActorName) match {
      case ref if ref.isTerminated =>
        context.actorOf(Props(new DocumentReceiver(Textify.apply, processDocument, retrievalResult)), ReceiverActorName)
      case existingReceiver => existingReceiver
    }
  }

  private def updateProgress: Unit = {
    requestsCompleted += 1
    val r = retrieverGenerator.documentsToRetrieve

    context.parent ! DocumentRetrieved(requestsCompleted, r)
    if (requestsCompleted == r) endRetrieval
  }
  
  private def endRetrieval: Unit = findOrCreateDocumentReceiver() ! Done(requestsCompleted, retrieverGenerator.totalDocuments)
  

  
}
