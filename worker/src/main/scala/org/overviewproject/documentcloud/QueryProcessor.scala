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
import org.overviewproject.documentcloud.DocumentReceiverProtocol.Done

/** Messages sent when interacting with QueryProcessor */
object QueryProcessorProtocol {
  /** Request a page of the query result */
  case class GetPage(page: Int)
}

/**
 *
 * Query result pages are added to the front of the queue to try to ensure that
 * there are always document requests available.
 *
 * @param query A DocumentCloud query string that will be url encoded.
 * @param credentials If provided, will be used to authenticate query and requests for private documents.
 * @param requestQueue The actor handling http requests
 */
class QueryProcessor(query: String, credentials: Option[Credentials], requestQueue: ActorRef) extends Actor {

  import QueryProcessorProtocol._

  private def createQueryUrlForPage(query: String, pageNum: Int): String = {
    s"https://www.documentcloud.org/api/search.json?per_page=$PageSize&page=$pageNum&q=$query"
  }

  private val PageSize: Int = Configuration.pageSize
  private val Encoding: String = "UTF-8"

  def receive = {
    case GetPage(pageNum) => requestPage(pageNum)
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

    context.parent ! result
  }
}