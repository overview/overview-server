package org.overviewproject.documentcloud

import java.net.URLEncoder
import org.overviewproject.documentcloud.DocumentRetrieverProtocol.{ Start => StartRetriever }
import org.overviewproject.http.PublicRequest
import org.overviewproject.http.RequestQueueProtocol._
import akka.actor._
import org.overviewproject.http.SimpleResponse
import scala.concurrent.Promise

object QueryProcessorProtocol {
  case class Start()
}

case class DocumentRetrievalError(url: String, message: String, statusCode: Option[Int] = None, headers: Option[String] = None)

class QueryProcessor(query: String, finished: Promise[Seq[DocumentRetrievalError]], processDocument: (Document, String) => Unit, requestQueue: ActorRef, retrieverGenerator: (Document, ActorRef) => Actor) extends Actor {
  import QueryProcessorProtocol._

  private def createQueryUrlForPage(query: String, pageNum: Int): String = {
    s"https://www.documentcloud.org/api/search.json?per_page=$PageSize&page=$pageNum&q=$query"
  }

  private val PageSize: Int = 20
  private val Encoding: String = "UTF-8"
  private val ReceiverActorName = "receiver"

  def receive = {
    case Start() => {
      requestPage(1)
    }
    case Result(response) => processResponse(response)
  }

  private def requestPage(pageNum: Int): Unit = {
    val encodedQuery = URLEncoder.encode(query, Encoding)
    val searchUrl = createQueryUrlForPage(encodedQuery, pageNum)

    requestQueue ! AddToFront(PublicRequest(searchUrl))
  }

  private def processResponse(response: SimpleResponse): Unit = {
    val result = ConvertSearchResult(response.body)

    if (morePagesAvailable(result)) requestPage(result.page + 1)

    val receiver = findOrCreateDocumentReceiver(result.total)
    spawnRetrievers(result.documents, receiver)
  }

  private def morePagesAvailable(result: SearchResult): Boolean = result.documents.size == PageSize

  private def findOrCreateDocumentReceiver(numberOfDocuments: Int): akka.actor.ActorRef = {
    context.actorFor(ReceiverActorName) match {
      case ref if ref.isTerminated =>
        context.actorOf(Props(new DocumentReceiver(processDocument, numberOfDocuments, finished)), ReceiverActorName)
      case existingReceiver => existingReceiver
    }
  }

  private def spawnRetrievers(documents: Seq[Document], receiver: ActorRef): Unit = documents.map { d =>
    val retriever = context.actorOf(Props(retrieverGenerator(d, receiver)))
    retriever ! StartRetriever()
  }
}