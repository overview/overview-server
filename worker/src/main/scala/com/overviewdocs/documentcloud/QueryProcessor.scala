package com.overviewdocs.documentcloud

import akka.actor._
import scala.util.{Failure,Success}
import com.ning.http.util.UTF8UrlEncoder

import com.overviewdocs.http.{Client=>HttpClient,Credentials,Response=>HttpResponse}
import com.overviewdocs.util.{Configuration,Logger}

/** Messages sent when interacting with QueryProcessor */
object QueryProcessorProtocol {
  /** Request a page of the query result */
  case class GetPage(page: Int)
}

/**
  * @param query A DocumentCloud query string that will be url encoded.
  * @param credentials If provided, will be used to authenticate query and requests for private documents.
  * @param httpClient The thing that does HTTP requests
  */
class QueryProcessor(
  query: String,
  credentials: Option[Credentials],
  httpClient: HttpClient,
  private val configuration : Configuration = Configuration
) extends Actor {

  import context.dispatcher
  import QueryProcessorProtocol._

  private val logger = Logger.forClass(getClass)
  private val DocumentCloudUrl = configuration.getString("documentcloud_url")

  private def createQueryUrlForPage(query: String, pageNum: Int): String = {
    s"$DocumentCloudUrl/api/search.json?per_page=$PageSize&page=$pageNum&q=${UTF8UrlEncoder.encodeQueryElement(query)}"
  }

  private val PageSize: Int = Configuration.getInt("page_size")

  def receive = {
    case GetPage(pageNum) => requestPage(pageNum)
  }

  private def requestPage(pageNum: Int): Unit = {
    logger.debug("Retrieving page {} of DocumentCloud results for query {}", pageNum, query)
    val searchUrl = createQueryUrlForPage(query, pageNum)

    httpClient.get(searchUrl, credentials).onComplete {
      case Success(response) => {
        if (context == null) return // happens during shutdown. ICK!
        val result = ConvertSearchResult(new String(response.bodyBytes, "utf-8")) // always utf-8: #85536256
        context.parent ! result
      }
      case Failure(error) => {
        if (context == null) return // happens during shutdown. ICK!
        // FIXME: Better to handle errors in supervisors
        context.parent ! Failure(error)
      }
    }
  }
}
