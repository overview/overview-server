package org.overviewproject.documentcloud

import akka.actor._
import java.net.URLEncoder
import org.overviewproject.http._
import org.overviewproject.http.RequestQueueProtocol._
import org.overviewproject.util.{Configuration,Logger}

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
class QueryProcessor(
    query: String,
    credentials: Option[Credentials],
    requestQueue: ActorRef,
    private val configuration : Configuration = Configuration
  ) extends Actor {

  import QueryProcessorProtocol._

  private val DocumentCloudUrl = configuration.getString("documentcloud_url")

  private def createQueryUrlForPage(query: String, pageNum: Int): String = {
    s"$DocumentCloudUrl/api/search.json?per_page=$PageSize&page=$pageNum&q=$query"
  }

  private val PageSize: Int = Configuration.getInt("page_size")
  private val Encoding: String = "UTF-8"

  def receive = {
    case GetPage(pageNum) => requestPage(pageNum)
    case Result(response) => processResponse(response)
    // FIXME: Better to handle errors in supervisors
    case Failure(error) => context.parent ! Failure(error)
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
