package org.overviewproject.documentcloud

import java.net.URLEncoder
import org.overviewproject.http.PublicRequest
import org.overviewproject.http.RequestQueueProtocol._
import akka.actor._
import org.overviewproject.http.SimpleResponse

object QueryProcessorProtocol {
  case class Start()
}

class QueryProcessor(requestQueue: ActorRef, query: String) extends Actor {
  import QueryProcessorProtocol._

  private val PageSize: Int = 20
  private val Encoding: String = "UTF-8"

  def receive = {
    case Start() => requestPage(1)
    case Result(response) => processResponse(response)
  }

  private def requestPage(pageNum: Int): Unit = {
    val encodedQuery = URLEncoder.encode(query, Encoding)
    val searchUrl = createQueryUrlForPage(encodedQuery, pageNum)

    requestQueue ! AddToFront(PublicRequest(searchUrl))
  }
  
  private def createQueryUrlForPage(query: String, pageNum: Int): String = {
    s"https://www.documentcloud.org/api/search.json?per_page=$PageSize&page=$pageNum&q=$query"
  }
  
  private def processResponse(response: SimpleResponse): Unit = {
    val result = ConvertSearchResult(response.body)
    
    if (morePagesAvailable(result)) requestPage(result.page + 1)
  }
  
  private def morePagesAvailable(result: SearchResult): Boolean = result.documents.size == PageSize
}