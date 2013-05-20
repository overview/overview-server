package org.overviewproject.jobhandler

import akka.actor.ActorContext
import org.overviewproject.database.orm.finders.SearchResultFinder
import akka.actor.Actor
import akka.actor.Props
import akka.actor.ActorRef

object SearchHandlerProtocol {
  case class Search(documentSetId: Long, query: String, requestQueue: ActorRef)
}

trait SearchHandlerComponents {
  val storage: Storage
  val actorCreator: ActorCreator
  
  class Storage {
    def searchExists(documentSetId: Long, query: String): Boolean = ???
  }
  
  class ActorCreator {
    def produceDocumentSearcher(documentSetId: Long, query: String, requestQueue: ActorRef): Actor = ???
  }
}

trait SearchHandler extends Actor {
  this: SearchHandlerComponents =>
    
  import SearchHandlerProtocol._
  import JobHandlerProtocol._
  import DocumentSearcherProtocol._
  
  def receive = {
    case Search(documentSetId, query, requestQueue) => search(documentSetId, query, requestQueue)
  }
  
  private def search(documentSetId: Long, query: String, requestQueue: ActorRef): Unit = {
    if (storage.searchExists(documentSetId, query)) context.parent ! Done
    else startSearch(documentSetId, query: String, requestQueue)
  }
    
  private def startSearch(documentSetId: Long, query: String, requestQueue: ActorRef): Unit = {
    val documentSearcher = 
      context.actorOf(Props(actorCreator.produceDocumentSearcher(documentSetId, query, requestQueue)))
      
      documentSearcher ! StartSearch()
  }
    
}
