package org.overviewproject.jobhandler

import org.overviewproject.database.Database
import org.overviewproject.database.orm.{SearchResult, SearchResultState}
import org.overviewproject.database.orm.finders.SearchResultFinder
import org.overviewproject.database.orm.stores.SearchResultStore
import org.overviewproject.jobhandler.JobHandlerProtocol.JobDone

import DocumentSearcherProtocol._
import akka.actor._


object SearchHandlerProtocol {
  case class Search(documentSetId: Long, query: String, requestQueue: ActorRef)
}

trait SearchHandlerComponents {
  val storage: Storage
  val actorCreator: ActorCreator

  trait Storage {
    def searchExists(documentSetId: Long, query: String): Boolean 
    def createSearchResult(documentSetId: Long, query: String): Long 
    def completeSearch(searchId: Long): Unit 
  }
  
  trait ActorCreator {
    def produceDocumentSearcher(documentSetId: Long, query: String, requestQueue: ActorRef): Actor     
  }
}

trait SearchHandler extends Actor {
  this: SearchHandlerComponents =>

  import SearchHandlerProtocol._
  import DocumentSearcherProtocol._

  def receive = {
    case Search(documentSetId, query, requestQueue) => search(documentSetId, query, requestQueue)
    case DocumentSearcherDone => context.parent ! JobDone
  }

  private def search(documentSetId: Long, query: String, requestQueue: ActorRef): Unit = {
    if (storage.searchExists(documentSetId, query)) context.parent ! JobDone
    else startSearch(documentSetId, query: String, requestQueue)
  }

  private def startSearch(documentSetId: Long, query: String, requestQueue: ActorRef): Unit = {
    val searchId = storage.createSearchResult(documentSetId, query)
    val documentSearcher =
      context.actorOf(Props(actorCreator.produceDocumentSearcher(documentSetId, query, requestQueue)))

    documentSearcher ! StartSearch(searchId)
  }

}



trait SearchHandlerComponentsImpl extends SearchHandlerComponents {
  
  class StorageImpl extends Storage {
    import org.overviewproject.database.orm.{ SearchResult, SearchResultState }

    override def searchExists(documentSetId: Long, query: String): Boolean = Database.inTransaction {
      SearchResultFinder.byDocumentSetAndQuery(documentSetId, query).headOption.isDefined
    }

    override def createSearchResult(documentSetId: Long, query: String): Long = Database.inTransaction {
      val searchResult = SearchResultStore.insertOrUpdate(
        SearchResult(SearchResultState.InProgress, documentSetId, query))

      searchResult.id
    }
    
    override def completeSearch(searchId: Long): Unit = ???
  }

  class ActorCreatorImpl extends ActorCreator {
    def produceDocumentSearcher(documentSetId: Long, query: String, requestQueue: ActorRef): Actor =
      new DocumentSearcher(documentSetId, query, requestQueue) with ActualQueryProcessorFactory
  }
}
