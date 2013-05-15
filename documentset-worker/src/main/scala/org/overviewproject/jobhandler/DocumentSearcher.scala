package org.overviewproject.jobhandler

import akka.actor._
import org.overviewproject.documentcloud.QueryProcessor

trait QueryProcessorFactory {
  def produce(query: String, requestQueue: ActorRef): Actor
}

class DocumentSearcher(documentSetId: Long, query: String, requestQueue: ActorRef) extends Actor {
  this: QueryProcessorFactory =>

  import org.overviewproject.documentcloud.QueryProcessorProtocol._

  private val queryProcessor = context.actorOf(Props(produce(createQuery, requestQueue)))
  
  queryProcessor ! GetPage(1)

  def receive = {
    case _ =>
  }
  
  private def createQuery: String = s"projectid:$documentSetId $query"
}


trait ActualQueryProcessorFactory extends QueryProcessorFactory {
  override def produce(query: String, requestQueue: ActorRef): Actor = new QueryProcessor(query, None, requestQueue)
}

class ActualDocumentSearcher(documentSetId: Long, query: String, requestQueue: ActorRef) extends
  DocumentSearcher(documentSetId, query, requestQueue) with ActualQueryProcessorFactory