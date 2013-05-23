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
    def completeSearch(searchId: Long, documentSetId: Long, query: String): Unit 
  }
  
  trait ActorCreator {
    def produceDocumentSearcher(documentSetId: Long, query: String, requestQueue: ActorRef): Actor     
  }
}

object SearchHandlerFSM {
  sealed trait State 
  case object Idle extends State
  case object Searching extends State
  
  sealed trait Data
  case object Uninitialized extends Data
  case class SearchInfo(searchResultId: Long, documentSetId: Long, query: String) extends Data
}

import SearchHandlerFSM._

trait SearchHandler extends Actor with FSM[State, Data] {
  this: SearchHandlerComponents =>

  import SearchHandlerProtocol._
  import DocumentSearcherProtocol._

  startWith(Idle, Uninitialized)
  
  when(Idle) {
    case Event(Search(documentSetId, query, requestQueue), _) =>
      if (storage.searchExists(documentSetId, query)) {
        context.parent ! JobDone
        goto(Idle) using Uninitialized
      }
      else {
        val searchId = storage.createSearchResult(documentSetId, query)
        startSearch(documentSetId, query, requestQueue, searchId)
        goto(Searching) using SearchInfo(searchId, documentSetId, query)
      }
  }
  
  when(Searching) {
    case Event(DocumentSearcherDone, SearchInfo(searchId, documentSetId, query)) =>
      context.parent ! JobDone
      storage.completeSearch(searchId, documentSetId, query)
      goto(Idle) using Uninitialized
  }
    
  initialize

  
  private def startSearch(documentSetId: Long, query: String, requestQueue: ActorRef, searchId: Long): Unit = {

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
    
    override def completeSearch(searchId: Long, documentSetId: Long, query: String): Unit =  Database.inTransaction {
      SearchResultStore.insertOrUpdate(SearchResult(SearchResultState.Complete, documentSetId, query, searchId))
    }
  }

  class ActorCreatorImpl extends ActorCreator {
    def produceDocumentSearcher(documentSetId: Long, query: String, requestQueue: ActorRef): Actor =
      new DocumentSearcher(documentSetId, query, requestQueue) with ActualQueryProcessorFactory
  }
}
