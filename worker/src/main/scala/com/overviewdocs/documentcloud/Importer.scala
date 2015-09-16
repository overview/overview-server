package com.overviewdocs.documentcloud

import scala.language.postfixOps
import scala.concurrent.Promise
import scala.concurrent.duration._

import com.overviewdocs.documentcloud.DocumentRetrieverManagerProtocol._
import com.overviewdocs.documentcloud.QueryProcessorProtocol._
import com.overviewdocs.http.{AsyncHttpClient,Credentials}
import com.overviewdocs.util.Configuration

import akka.actor._

object ImporterProtocol {
  case class StartImport()
}


class Importer(
  query: String,
  credentials: Option[Credentials],
  retrieverGenerator: RetrieverGenerator,
  processDocument: (Document, String) => Unit,
  maxDocuments: Int,
  reportProgress: (Int, Int) => Unit,
  importResult: Promise[RetrievalResult]
) extends Actor {

  import ImporterProtocol._

  private val asyncHttpClient = new AsyncHttpClient
  private val queryProcessor: ActorRef = context.actorOf(Props(new QueryProcessor(query, credentials, asyncHttpClient)))
  private val retrieverManager: ActorRef = context.actorOf(Props(new DocumentRetrieverManager(retrieverGenerator, processDocument, importResult, maxDocuments)))
  
  def receive = {
    case StartImport() => queryProcessor ! GetPage(1)
    case result: SearchResult => retrieverManager ! Retrieve(result)
    case GetSearchResultPage(n) => queryProcessor ! GetPage(n)
    case DocumentRetrieved(n, total) => reportProgress(n, total)
  }
}
