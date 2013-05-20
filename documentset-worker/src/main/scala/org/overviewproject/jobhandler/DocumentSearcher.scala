package org.overviewproject.jobhandler

import akka.actor._
import org.overviewproject.documentcloud.QueryProcessor
import org.overviewproject.documentcloud.SearchResult
import org.overviewproject.util.Configuration

trait DocumentSearcherComponents {
  def produceQueryProcessor(query: String, requestQueue: ActorRef): Actor
  def produceSearchSaver: Actor
}

object DocumentSearcherProtocol {
  case class StartSearch()
}


class DocumentSearcher(documentSetId: Long, query: String, requestQueue: ActorRef,
  pageSize: Int = Configuration.pageSize, maxDocuments: Int = Configuration.maxDocuments) extends Actor {
  this: DocumentSearcherComponents =>

  import DocumentSearcherProtocol._
  import org.overviewproject.documentcloud.QueryProcessorProtocol._
  import org.overviewproject.jobhandler.SearchSaverProtocol._
  
  private val queryProcessor = context.actorOf(Props(produceQueryProcessor(createQuery, requestQueue)))
  private val searchSaver = context.actorOf(Props(produceSearchSaver))
  
  def receive = {
    case StartSearch() => queryProcessor ! GetPage(1)
    case SearchResult(total, page, documents) => {
      val documentsFromPage = scala.math.min(pageSize,  maxDocuments - (page - 1) * pageSize)
      searchSaver ! Save(documents.take(documentsFromPage))
      
      if (page == 1) requestRemainingPages(total)
    }
  }

  private def createQuery: String = s"projectid:$documentSetId $query"

  private def requestRemainingPages(total: Int): Unit = {
    val totalPages: Int = scala.math.min(total, maxDocuments) / pageSize
    for (p <- 2 to totalPages)
      queryProcessor ! GetPage(p)
  }
}

trait ActualQueryProcessorFactory extends DocumentSearcherComponents {
  override def produceQueryProcessor(query: String, requestQueue: ActorRef): Actor = new QueryProcessor(query, None, requestQueue)
  override def produceSearchSaver: Actor = new SearchSaver()
}

class ActualDocumentSearcher(documentSetId: Long, query: String, requestQueue: ActorRef) extends DocumentSearcher(documentSetId, query, requestQueue) with ActualQueryProcessorFactory