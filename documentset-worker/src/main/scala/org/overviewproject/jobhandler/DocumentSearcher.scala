package org.overviewproject.jobhandler

import akka.actor._
import org.overviewproject.documentcloud.QueryProcessor
import org.overviewproject.documentcloud.SearchResult
import org.overviewproject.util.Configuration

trait QueryProcessorFactory {
  def produce(query: String, requestQueue: ActorRef): Actor
}

class DocumentSearcher(documentSetId: Long, query: String, requestQueue: ActorRef) extends Actor {
  this: QueryProcessorFactory =>

  import org.overviewproject.documentcloud.QueryProcessorProtocol._

  private val PageSize: Int = Configuration.pageSize
  
  private val queryProcessor = context.actorOf(Props(produce(createQuery, requestQueue)))
  
  queryProcessor ! GetPage(1)

  def receive = {
    case SearchResult(total, page, documents) => if (page == 1) requestRemainingPages(total)
  }
  
  private def createQuery: String = s"projectid:$documentSetId $query"
  
  private def requestRemainingPages(total: Int): Unit = {
    val totalPages: Int = total / PageSize 
    for (p <- 2 to totalPages) 
      queryProcessor ! GetPage(p)
  }
}


trait ActualQueryProcessorFactory extends QueryProcessorFactory {
  override def produce(query: String, requestQueue: ActorRef): Actor = new QueryProcessor(query, None, requestQueue)
}

class ActualDocumentSearcher(documentSetId: Long, query: String, requestQueue: ActorRef) extends
  DocumentSearcher(documentSetId, query, requestQueue) with ActualQueryProcessorFactory