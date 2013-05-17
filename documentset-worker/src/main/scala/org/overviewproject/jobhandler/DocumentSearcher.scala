package org.overviewproject.jobhandler

import akka.actor._
import org.overviewproject.documentcloud.QueryProcessor
import org.overviewproject.documentcloud.SearchResult
import org.overviewproject.util.Configuration

trait DocumentSearcherComponents {
  def produceQueryProcessor(query: String, requestQueue: ActorRef): Actor
  def produceSearchSaver: Actor
}

class DocumentSearcher(documentSetId: Long, query: String, requestQueue: ActorRef,
  pageSize: Int = Configuration.pageSize, maxDocuments: Int = Configuration.maxDocuments) extends Actor {
  this: DocumentSearcherComponents =>

  import org.overviewproject.documentcloud.QueryProcessorProtocol._
  import org.overviewproject.jobhandler.SearchSaverProtocol._
  
  private val queryProcessor = context.actorOf(Props(produceQueryProcessor(createQuery, requestQueue)))
  private val searchSaver = context.actorOf(Props(produceSearchSaver))
  
  queryProcessor ! GetPage(1)

  def receive = {
    case SearchResult(total, page, documents) => {
      val lastPage: Int = scala.math.ceil(maxDocuments.toDouble / pageSize.toDouble).toInt

      if (page == lastPage) {
        val documentsFromLastPage = scala.math.min(pageSize,  maxDocuments - (page - 1) * pageSize)
        searchSaver ! Save(documents.take(documentsFromLastPage))
      }
      else {
        searchSaver ! Save(documents)  
      }
      
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