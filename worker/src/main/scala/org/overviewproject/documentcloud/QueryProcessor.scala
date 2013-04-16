package org.overviewproject.documentcloud

import java.net.URLEncoder
import org.overviewproject.documentcloud.DocumentRetrieverProtocol.{ Start => StartRetriever }
import org.overviewproject.http.PublicRequest
import org.overviewproject.http.RequestQueueProtocol._
import akka.actor._
import org.overviewproject.http.SimpleResponse
import scala.concurrent.Promise
import org.overviewproject.http.Credentials
import org.overviewproject.http.Request
import org.overviewproject.http.PrivateRequest
import org.overviewproject.util.Logger

object QueryProcessorProtocol {
  case class Start()
}

case class DocumentRetrievalError(url: String, message: String, statusCode: Option[Int] = None, headers: Option[String] = None)

class QueryInformation {
  val documentsTotal = Promise[Int]
  val errors = Promise[Seq[DocumentRetrievalError]]
}

class QueryProcessor(query: String, queryInformation: QueryInformation, credentials: Option[Credentials], maxDocuments: Int,
  processDocument: (Document, String) => Unit, requestQueue: ActorRef, retrieverGenerator: (Document, ActorRef) => Actor) extends Actor {

  import QueryProcessorProtocol._

  var documentsToRetrieve: Option[Int] = None

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
    Logger.debug(s"Retrieving DocumentCloud results for query $query, page $pageNum")
    val encodedQuery = URLEncoder.encode(query, Encoding)
    val searchUrl = createQueryUrlForPage(encodedQuery, pageNum)

    requestQueue ! AddToFront(createRequest(searchUrl))
  }

  private def createRequest(url: String): Request = credentials match {
    case Some(c) => PrivateRequest(url, c)
    case None => PublicRequest(url)
  }

  private def processResponse(response: SimpleResponse): Unit = {
    val result = ConvertSearchResult(response.body)
    setDocumentsTotal(result.total)

    documentsToRetrieve.map { t =>
      val receiver = findOrCreateDocumentReceiver(t)

      if (morePagesAvailable(result, t)) {
        requestPage(result.page + 1)
        spawnRetrievers(result.documents, receiver)
      }
      else {
        val documentsInLastPage = t - (result.page - 1) * PageSize
        spawnRetrievers(result.documents.take(documentsInLastPage), receiver)
      }
    }
  }

  private def setDocumentsTotal(n: Int) =
    if (!queryInformation.documentsTotal.isCompleted) {
      documentsToRetrieve = Some(scala.math.min(maxDocuments, n))
      queryInformation.documentsTotal.success(n)
    }

  private def morePagesAvailable(result: SearchResult, documentsToRetrieve: Int): Boolean = result.page * PageSize < documentsToRetrieve

  private def findOrCreateDocumentReceiver(numberOfDocuments: Int): akka.actor.ActorRef = {
    context.actorFor(ReceiverActorName) match {
      case ref if ref.isTerminated =>
        context.actorOf(Props(new DocumentReceiver(processDocument, numberOfDocuments, queryInformation.errors)), ReceiverActorName)
      case existingReceiver => existingReceiver
    }
  }

  private def spawnRetrievers(documents: Seq[Document], receiver: ActorRef): Unit = documents.map { d =>
    val retriever = context.actorOf(Props(retrieverGenerator(d, receiver)))
    retriever ! StartRetriever()
  }
}