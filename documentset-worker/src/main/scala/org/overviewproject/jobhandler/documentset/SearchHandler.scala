package org.overviewproject.jobhandler.documentset

import akka.actor._

import org.overviewproject.database.Database
import org.overviewproject.database.orm.finders.SearchResultFinder
import org.overviewproject.database.orm.stores.SearchResultStore
import org.overviewproject.jobhandler.JobProtocol._
import org.overviewproject.searchindex.ElasticSearchComponents
import org.overviewproject.util.Logger

import SearchHandlerFSM._


/** Message sent to the SearchHandler */
object SearchHandlerProtocol {
  case class SearchDocumentSet(documentSetId: Long, query: String)
}

/**
 * The SearchHandler goes through the following state transitions:
 * Idle -> Searching: when receiving a search message for a new search
 * Idle -> Idle: when receiving a search message for an existing search
 * Searching -> stop: when DocumentSearcherDone or Failure is received
 * 
 */
object SearchHandlerFSM {
  sealed trait State 
  case object Idle extends State
  case object Searching extends State
  
  sealed trait Data
  case object Uninitialized extends Data
  case class SearchInfo(searchResultId: Long, documentSetId: Long, query: String) extends Data
}

/**
 * The `SearchHandler` interacts with the database through the `storage` component
 * and uses `actorCreator` to create child actors.
 */
trait SearchHandlerComponents {
  val storage: Storage
  val actorCreator: ActorCreator
  
  trait Storage {
    /** @return a query combining the original document set query with the search terms */
    def queryForProject(documentSetId: Long, searchTerms: String): String
    
    /** @return true if a 'SearchResult' exists with the given parameters */
    def searchExists(documentSetId: Long, searchTerms: String): Boolean 
    
    /** create a new `SearchResult` with state `InProgress` */
    def createSearchResult(documentSetId: Long, searchTerms: String): Long 
    
    /** mark the `SearchResult` as `Complete` */
    def completeSearch(searchId: Long, documentSetId: Long, query: String): Unit 
    
    /** set the `SearchResult` state to `Error` */
    def failSearch(searchId: Long, documentSetId: Long, query: String): Unit
  }
  
  trait ActorCreator {
    def produceDocumentSearcher(documentSetId: Long, query: String): Actor     
  }
}

/**
 * Manages a `SearchResult`. When a search request arrives, check whether a `SearchResult` entry
 * already exists for the document set with the same query. If not, 
 * starts a new search, marking the `SearchResult` as `Complete` when all results have been retrieved.
 */
trait SearchHandler extends Actor with FSM[State, Data] {
  this: SearchHandlerComponents =>

  import SearchHandlerProtocol._
  import org.overviewproject.jobhandler.documentset.SearchIndexSearcherProtocol._
  
  startWith(Idle, Uninitialized)
  
  when(Idle) {
    case Event(SearchDocumentSet(documentSetId, query), _) =>
      if (storage.searchExists(documentSetId, query)) {
        context.parent ! JobDone(documentSetId)
        goto(Idle) using Uninitialized
      }
      else {
        Logger.info(s"Starting search [$documentSetId]: $query")
        val searchId = storage.createSearchResult(documentSetId, query)
        startSearch(documentSetId, query, searchId)
        goto(Searching) using SearchInfo(searchId, documentSetId, query)
      }
  }
  
  when(Searching) {
    case Event(SearchComplete, SearchInfo(searchId, documentSetId, query)) =>
      Logger.info(s"Search complete [$documentSetId]: $query")
      context.parent ! JobDone(documentSetId)
      storage.completeSearch(searchId, documentSetId, query)
      stop()
    case Event(SearchFailure(e), SearchInfo(searchId, documentSetId, query)) =>
      Logger.error(s"Search failed $query", e)
      context.parent ! JobDone(documentSetId)
      storage.failSearch(searchId, documentSetId, query)
      stop()
  }
    
  initialize

  
  private def startSearch(documentSetId: Long, searchTerms: String, searchId: Long): Unit = {

    val query = storage.queryForProject(documentSetId, searchTerms)
    val documentSearcher =
      context.actorOf(Props(actorCreator.produceDocumentSearcher(documentSetId, query)))

    documentSearcher ! StartSearch(searchId, documentSetId, query)
  }

}



trait SearchHandlerComponentsImpl extends SearchHandlerComponents {
  
  class StorageImpl extends Storage {
    import org.overviewproject.tree.orm.{ SearchResult, SearchResultState }

    override def queryForProject(documentSetId: Long, searchTerms: String): String = searchTerms
    
    override def searchExists(documentSetId: Long, searchTerms: String): Boolean = Database.inTransaction {
      SearchResultFinder.byDocumentSetAndQuery(documentSetId, searchTerms).headOption.isDefined
    }

    override def createSearchResult(documentSetId: Long, searchTerms: String): Long = Database.inTransaction {
      val searchResult = SearchResultStore.insertOrUpdate(
        SearchResult(SearchResultState.InProgress, documentSetId, searchTerms))

      searchResult.id
    }
    
    override def completeSearch(searchId: Long, documentSetId: Long, query: String): Unit =  Database.inTransaction {
      SearchResultStore.insertOrUpdate(SearchResult(SearchResultState.Complete, documentSetId, query, id = searchId))
    }
    
    override def failSearch(searchId: Long, documentSetId: Long, query: String): Unit = Database.inTransaction {
      SearchResultStore.insertOrUpdate(SearchResult(SearchResultState.Error, documentSetId, query, id = searchId))
    }
  }

  class ActorCreatorImpl extends ActorCreator {
    def produceDocumentSearcher(documentSetId: Long, query: String): Actor =
      new SearchIndexSearcher with ElasticSearchComponents
  }
}
