package org.overviewproject.documentcloud

import java.net.URLEncoder
import scala.concurrent.Promise
import org.overviewproject.documentcloud.DocumentRetrieverProtocol.{ Start => StartRetriever }
import org.overviewproject.http._
import org.overviewproject.http.RequestQueueProtocol._
import org.overviewproject.util.Logger
import akka.actor._
import org.overviewproject.util.Configuration
import org.overviewproject.documentcloud.DocumentRetrieverProtocol.JobComplete

/** Messages sent when interacting with QueryProcessor */
object QueryProcessorProtocol {
  /** Start the query */
  case class Start()
}

/** Information about documents that could not be retrieved */
case class DocumentRetrievalError(url: String, message: String, statusCode: Option[Int] = None, headers: Option[String] = None)

/**
 * Information about the query that will be provided as the query progresses.
 */
class QueryInformation {
  /**
   *  The total number of documents in the DocumentCloud query result.
   *  The value reflects the number specified at the start of the query.
   *  The Promise will be fulfilled after the QueryProcessor receives the first
   *  page of query results.
   */
  val documentsTotal = Promise[Int]

  /**
   * Information about all documents that could not be retrieved.
   * The Promise will be fulfilled when the query processing is complete.
   * The Promise will fail if the query processing was aborted because of errors.
   */
  val errors = Promise[Seq[DocumentRetrievalError]]
}

/**
 * Handles a DocumentCloud query.
 * Requests query results in pages, then creates a retriever actor for each document in the result.
 * A DocumentReceiver actor is created and specified as the ultimate recipient of the document text.
 * The DocumentReceiver is responsible for completing the queryInformation.errors promise.
 *
 * Query result pages are added to the front of the queue to try to ensure that
 * there are always document requests available.
 *
 * @param query A DocumentCloud query string that will be url encoded.
 * @param queryInformation Contains the Promises of information from the query. queryInformation.errors will be fulfilled
 * when the query is complete.
 * @param credentials If provided, will be used to authenticate query and requests for private documents.
 * @param maxDocuments The maximum number of documents to attempt to retrieve
 * @param processDocument Callback that will be called when a document is retrieved.
 * @param reportProgress Callback for updating progress FIXME: this should be handled by separate actor
 * @param requestQueue The actor handling http requests
 * @param retrieverGenerator A function for generating actors that will retrieve documents. Will be passed
 * the document and a reference to the document receiver.
 */
class QueryProcessor(query: String, queryInformation: QueryInformation, credentials: Option[Credentials], maxDocuments: Int,
  processDocument: (Document, String) => Unit, reportProgress: (Int, Int) => Unit, requestQueue: ActorRef, retrieverGenerator: (Document, ActorRef) => Actor) extends Actor {

  import QueryProcessorProtocol._

  /**
   * The number of documents that we will attempt to retrieve.
   * Set by the `total` value in the first page of the query search result.
   * Will be at most `maxDocuments`.
   */
  var documentsToRetrieve: Option[Int] = None
  
  /** The number of completed retrievals */
  var completedRetrievals: Int = 0
  
  private def createQueryUrlForPage(query: String, pageNum: Int): String = {
    s"https://www.documentcloud.org/api/search.json?per_page=$PageSize&page=$pageNum&q=$query"
  }

  private val PageSize: Int = Configuration.pageSize
  private val Encoding: String = "UTF-8"
  private val ReceiverActorName = "receiver"

  def receive = {
    case Start() => {
      requestPage(1)
    }
    case Result(response) => processResponse(response)
    case JobComplete() => updateProgress
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

 
  private def updateProgress: Unit = {
    completedRetrievals += 1
    documentsToRetrieve.map { t => reportProgress(completedRetrievals, t) }
  }
  
  /**
   * When a query result page is received, request the next page if available and maxDocuments
   * has not been reached. Spawn retrievers for each document.
   * Fulfill the documentsTotal promise after the first page.
   */
  private def processResponse(response: SimpleResponse): Unit = {
    val result = ConvertSearchResult(response.body)
    setDocumentsTotal(result.total)

    documentsToRetrieve.map { t =>
      val receiver = findOrCreateDocumentReceiver(t)

      if (morePagesAvailable(result, t)) {
        requestPage(result.page + 1)
        spawnRetrievers(result.documents, receiver)
      } else {
        val documentsInLastPage = t - (result.page - 1) * PageSize
        spawnRetrievers(result.documents.take(documentsInLastPage), receiver)
      }
    }
  }

  /** Complete the documentsTotal promise. If already completed, the value is ignored.   */
  private def setDocumentsTotal(n: Int) =
    if (!queryInformation.documentsTotal.isCompleted) {
      documentsToRetrieve = Some(scala.math.min(maxDocuments, n))
      queryInformation.documentsTotal.success(n)
    }

  private def morePagesAvailable(result: SearchResult, documentsToRetrieve: Int): Boolean = result.page * PageSize < documentsToRetrieve

  def findOrCreateDocumentReceiver(numberOfDocuments: Int): akka.actor.ActorRef = {
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