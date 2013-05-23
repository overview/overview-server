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
  case class StartSearch(searchResultId: Long)
  case object DocumentSearcherDone
}

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

import DocumentSearcherFSM._

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
      val totalDocuments = scala.math.min(total, maxDocuments)
      val totalPages: Int = scala.math.ceil(totalDocuments.toFloat / pageSize).toInt
      
      requestRemainingPages(totalPages)
      val documentsFromPage = scala.math.min(pageSize, maxDocuments - (page - 1) * pageSize)
      searchSaver ! Save(id, documentSetId, documents.take(documentsFromPage))

      if (totalPages == 1) {
        context.watch(searchSaver)
        searchSaver ! PoisonPill
        goto(WaitingForSearchSaverEnd) using SearchInfo(id, totalPages, 1)
      }
      else goto(RetrievingSearchResults) using SearchInfo(id, totalPages, 1)
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
  }
  
  when(WaitingForSearchSaverEnd) {
    case Event(Terminated(a), SearchInfo(id, t, p)) => { 
      context.parent ! DocumentSearcherDone
      goto(Idle) using Uninitialized
    }
        
  }

  initialize

  private def createQuery: String = s"projectid:$documentSetId $query"

  private def requestRemainingPages(totalPages: Int): Unit = 
    for (p <- 2 to totalPages)
      queryProcessor ! GetPage(p)

}

trait ActualQueryProcessorFactory extends DocumentSearcherComponents {
  override def produceQueryProcessor(query: String, requestQueue: ActorRef): Actor = new QueryProcessor(query, None, requestQueue)
  override def produceSearchSaver: Actor = new SearchSaver with ActualSearchSaverComponents
}

class ActualDocumentSearcher(documentSetId: Long, query: String, requestQueue: ActorRef) extends DocumentSearcher(documentSetId, query, requestQueue) with ActualQueryProcessorFactory