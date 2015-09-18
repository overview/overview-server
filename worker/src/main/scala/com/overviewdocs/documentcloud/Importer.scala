package com.overviewdocs.documentcloud

import scala.language.postfixOps
import scala.concurrent.Promise
import scala.concurrent.duration._

import com.overviewdocs.documentcloud.DocumentRetrieverManagerProtocol._
import com.overviewdocs.documentcloud.QueryProcessorProtocol._
import com.overviewdocs.http.{Credentials, RequestQueue, NingClient}
import com.overviewdocs.util.Configuration

import akka.actor._

object ImporterProtocol {
  case class StartImport()
}


class Importer(
  query: String, credentials: Option[Credentials],
  retrieverGenerator: RetrieverGenerator, processDocument: (Document, String) => Unit, maxDocuments: Int,
  reportProgress: (Int, Int) => Unit,
  importResult: Promise[RetrievalResult]) extends Actor {

  import ImporterProtocol._

  private val MaxInFlightRequests = Configuration.getInt("max_inflight_requests")
  private val RequestQueueName = "requestqueue"

  private val httpClient = new NingClient
  private val requestQueue: ActorRef = context.actorOf(Props(new RequestQueue(httpClient, MaxInFlightRequests)), RequestQueueName)
  private val queryProcessor: ActorRef = context.actorOf(Props(new QueryProcessor(query, credentials, requestQueue)))
  
  private val retrieverManager: ActorRef = context.actorOf(Props(new DocumentRetrieverManager(retrieverGenerator, processDocument, importResult, maxDocuments)))
  
  def receive = {
    case StartImport() => queryProcessor ! GetPage(1)
    case result: SearchResult => retrieverManager ! Retrieve(result)
    case GetSearchResultPage(n) => queryProcessor ! GetPage(n)
    case DocumentRetrieved(n, total) => reportProgress(n, total)
  }
}
