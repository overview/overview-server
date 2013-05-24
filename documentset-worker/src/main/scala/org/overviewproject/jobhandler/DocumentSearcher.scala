package org.overviewproject.jobhandler

import akka.actor._
import org.overviewproject.documentcloud.{ QueryProcessor, SearchResult }
import org.overviewproject.util.Configuration

import DocumentSearcherFSM._
import org.overviewproject.http.RequestQueueProtocol.Failure


/** Messages for interacting with DocumentSearcher */
object DocumentSearcherProtocol {
  case class StartSearch(searchResultId: Long)
  case object DocumentSearcherDone
}


/**
 * `DocumentSearcher` goes through the following state transitions:
 * Idle -> WaitingForSearchInfo: when a search request has been received, and the first
 *                               page of results has been requested from documentcloud.
 * WaitingForSearchInfo -> RetrievingSearchResults: when the first page of results has been received, and
 *                                                  the remaining pages have been requested.
 * WaitingForSearchInfo -> WaitingForSearchSaverEnd: when the first page of results has been received and
 *                                                   there are no more pages to be retrieved.
 * RetrievingSearchResults -> RetrievingSearchResults: when search result pages arrive
 * RetrievingSearchResults -> WaitingForSearchSaverEnd: when all search result pages have been received      
 * WaitingForSearchSaverEnd -> Idle: when the SearchSaver has finished saving all received search results.                                                                                             
 */
object DocumentSearcherFSM {
  sealed trait State
  case object Idle extends State
  case object WaitingForSearchInfo extends State
  case object RetrievingSearchResults extends State
  case object WaitingForSearchSaverEnd extends State

  sealed trait Data
  case object Uninitialized extends Data
  case class SearchResultId(id: Long) extends Data
  case class SearchInfo(searchResultId: Long, total: Int, pagesRetrieved: Int) extends Data
}



// I'm straying a bit from the usual pattern of having an actorCreator component.
// Because actor creation can't be mocked we need to simply override the methods
// in our tests, so a component variable doesn't help much.
trait DocumentSearcherComponents {
  def produceQueryProcessor(query: String, requestQueue: ActorRef): Actor
  def produceSearchSaver: Actor
}

/**
 * Queries documentCloud using the public search api, and sends the results to a `SearchSaver` actor
 * to be stored in the database. Requests the first page, then uses the information to requests any
 * remaining pages.
 * As results arrive, they are sent to the `SearchSaver`. When the last page of results is received,
 * those results are also sent to the `SearchSaver` followed by a `PoisonPill`. The `SearchSaver` will
 * therefore terminate, after having processed all previous results. 
 * When the 'DocumentSearcher' is notified that the `SearchSaver` is terminated, it notifies the parent
 * actor that the search is complete.
 */
class DocumentSearcher(documentSetId: Long, query: String, requestQueue: ActorRef,
  pageSize: Int = Configuration.pageSize, maxDocuments: Int = Configuration.maxDocuments) extends Actor
  with FSM[State, Data] {
  this: DocumentSearcherComponents =>

  import DocumentSearcherProtocol._
  import org.overviewproject.documentcloud.QueryProcessorProtocol._
  import org.overviewproject.jobhandler.SearchSaverProtocol._

  private val queryProcessor = context.actorOf(Props(produceQueryProcessor(createQuery, requestQueue)))
  private val searchSaver = context.actorOf(Props(produceSearchSaver))

  startWith(Idle, Uninitialized)

  when(Idle) {
    case Event(StartSearch(id), Uninitialized) =>
      queryProcessor ! GetPage(1)
      goto(WaitingForSearchInfo) using SearchResultId(id)
  }

  when(WaitingForSearchInfo) {
    case Event(SearchResult(total, page, documents), SearchResultId(id)) => {
      val totalDocuments = scala.math.min(total, maxDocuments) // don't retrieve more than maxDocuments results
      val totalPages: Int = scala.math.ceil(totalDocuments.toFloat / pageSize).toInt
      
      requestRemainingPages(totalPages)
      val documentsFromPage = scala.math.min(pageSize, maxDocuments - (page - 1) * pageSize)
      searchSaver ! Save(id, documentSetId, documents.take(documentsFromPage))

      if (totalPages <= 1) {
        context.watch(searchSaver)
        searchSaver ! PoisonPill
        goto(WaitingForSearchSaverEnd) using SearchInfo(id, totalPages, 1)
      }
      else goto(RetrievingSearchResults) using SearchInfo(id, totalPages, 1)
    }
    case Event(Failure(e), _) => {
      context.parent ! Failure(e)
      goto(Idle) using Uninitialized
    }
  }

  when(RetrievingSearchResults) {
    case Event(SearchResult(_, page, documents), SearchInfo(id, totalPages, pagesRetrieved)) => {
      val documentsFromPage = scala.math.min(pageSize, maxDocuments - (page - 1) * pageSize)
      searchSaver ! Save(id, documentSetId, documents.take(documentsFromPage))

      val currentPagesRetrieved = pagesRetrieved + 1
      
      if (currentPagesRetrieved == totalPages) { 
        context.watch(searchSaver)
        searchSaver ! PoisonPill  
        goto(WaitingForSearchSaverEnd) using SearchInfo(id, totalPages, currentPagesRetrieved)
      }
      else stay using SearchInfo(id, totalPages, currentPagesRetrieved)
    }
    // If one of the page queries fails, we will notify the parent
    // but still wait for the remainder of the queries to come in
    // Better to just stop the actor (or to handle exception through
    // supervisor)
    case Event(Failure(e), SearchInfo(id, totalPages, pagesRetrieved)) => {
      context.parent ! Failure(e)
      
      val currentPagesRetrieved = pagesRetrieved + 1
      
      if (currentPagesRetrieved == totalPages) { 
        context.watch(searchSaver)
        searchSaver ! PoisonPill  
        goto(Idle) using Uninitialized // don't want to tell parent we finish twice.
      }
      else stay using SearchInfo(id, totalPages, currentPagesRetrieved)
    }

  }
  
  when(WaitingForSearchSaverEnd) {
    case Event(Terminated(a), SearchInfo(id, t, p)) => { 
      context.parent ! DocumentSearcherDone
      goto(Idle) using Uninitialized
    }
        
  }

  initialize

  private def createQuery: String = s"projectid:$documentSetId $query"

  // We can request all remaining pages at the same time.
  // the request queue will throttle requests, and we don't 
  // care about the order in which results are returned.
  private def requestRemainingPages(totalPages: Int): Unit = 
    for (p <- 2 to totalPages)
      queryProcessor ! GetPage(p)

}


/** Create the actual actors */
trait DocumentSearcherComponentsImpl extends DocumentSearcherComponents {
  override def produceQueryProcessor(query: String, requestQueue: ActorRef): Actor = new QueryProcessor(query, None, requestQueue)
  override def produceSearchSaver: Actor = new SearchSaver with ActualSearchSaverComponents
}

