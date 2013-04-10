package org.overviewproject.documentcloud

import java.net.URLEncoder

import org.overviewproject.http.PublicRequest
import org.overviewproject.http.RequestQueueProtocol.AddToFront

import akka.actor.{Actor, ActorRef, actorRef2Scala}

object QueryProcessorProtocol {
  case class Query(query: String)
}

class QueryProcessor(requestQueue: ActorRef) extends Actor {
  import QueryProcessorProtocol._

  private val PageSize: Int = 20

  def receive = {
    case Query(query) => requestQueryPage(query)
  }

  private def requestQueryPage(query: String): Unit = {
    val encodedQuery = URLEncoder.encode(query, "UTF-8")
    val searchUrl = s"https://www.documentcloud.org/api/search.json?per_page=$PageSize&page=1&q=$encodedQuery"

    requestQueue ! AddToFront(PublicRequest(searchUrl))
  }
}