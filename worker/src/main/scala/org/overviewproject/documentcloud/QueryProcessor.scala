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

object QueryProcessorProtocol {
  case class Start()
}

case class DocumentRetrievalError(url: String, message: String, statusCode: Option[Int] = None, headers: Option[String] = None)

class QueryInformation {
  val documentsTotal = Promise[Int]
  val errors = Promise[Seq[DocumentRetrievalError]]
}

class QueryProcessor(query: String, queryInformation: QueryInformation, credentials: Option[Credentials], processDocument: (Document, String) => Unit, requestQueue: ActorRef, retrieverGenerator: (Document, ActorRef) => Actor) extends Actor {
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
    
    requestQueue ! AddToFront(createRequest(searchUrl))
  }
  
  private def createRequest(url: String): Request = credentials match {
    case Some(c) => PrivateRequest(url, c)
    case None => PublicRequest(url)
  }    
    

  private def processResponse(response: SimpleResponse): Unit = {
    val result = ConvertSearchResult(response.body)
    setDocumentsTotal(result.total)

    if (morePagesAvailable(result)) requestPage(result.page + 1)

    val receiver = findOrCreateDocumentReceiver(result.total)
    spawnRetrievers(result.documents, receiver)
  }

  private def setDocumentsTotal(n: Int) =
    if (!queryInformation.documentsTotal.isCompleted) queryInformation.documentsTotal.success(n)

  private def morePagesAvailable(result: SearchResult): Boolean = result.documents.size == PageSize

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