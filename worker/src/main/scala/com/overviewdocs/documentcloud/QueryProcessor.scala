package com.overviewdocs.documentcloud

import akka.actor._
import java.net.URLEncoder

import com.overviewdocs.http._
import com.overviewdocs.http.RequestQueueProtocol._
import com.overviewdocs.util.{Configuration,Logger}

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

  private val logger = Logger.forClass(getClass)
  private val DocumentCloudUrl = configuration.getString("documentcloud_url")

  private def createQueryUrlForPage(pageNum: Int): String = {
    val encodedQuery = URLEncoder.encode(query, Encoding)
    s"$DocumentCloudUrl/api/search.json?per_page=$PageSize&page=$pageNum&q=$encodedQuery"
  }

  private val PageSize: Int = Configuration.getInt("page_size")
  private val Encoding: String = "UTF-8"

  def receive = {
    case GetPage(pageNum) => requestPage(pageNum)
    case HttpSuccess(response) => context.parent ! ConvertSearchResult(response.body)
    case HttpFailure(error) => context.parent ! HttpFailure(error)
  }

  private def requestPage(pageNum: Int): Unit = {
    logger.debug("Retrieving page {} of DocumentCloud results for query {}", pageNum, query)
    val searchUrl = createQueryUrlForPage(pageNum)

    requestQueue ! AddToFront(Request(searchUrl, credentials))
  }
}
