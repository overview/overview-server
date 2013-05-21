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
}

object DocumentSearcherFSM {
  sealed trait State
  case object Idle extends State
  case object WaitingForSearchInfo extends State
  case object RetrievingSearchResults extends State

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
      requestRemainingPages(total)
      val documentsFromPage = scala.math.min(pageSize, maxDocuments - (page - 1) * pageSize)
      searchSaver ! Save(id, documentSetId, documents.take(documentsFromPage))

      goto(RetrievingSearchResults) using SearchInfo(id, total, 1)
    }
  }

  when(RetrievingSearchResults) {
    case Event(SearchResult(_, page, documents), SearchInfo(id, total, pagesRetrieved)) => {
      val documentsFromPage = scala.math.min(pageSize, maxDocuments - (page - 1) * pageSize)
      searchSaver ! Save(id, documentSetId, documents.take(documentsFromPage))

      stay using SearchInfo(id, total, pagesRetrieved + 1)
    }
  }


  initialize

  private def createQuery: String = s"projectid:$documentSetId $query"

  private def requestRemainingPages(total: Int): Unit = {
    val totalPages: Int = scala.math.min(total, maxDocuments) / pageSize
    for (p <- 2 to totalPages)
      queryProcessor ! GetPage(p)
  }
}

trait ActualQueryProcessorFactory extends DocumentSearcherComponents {
  override def produceQueryProcessor(query: String, requestQueue: ActorRef): Actor = new QueryProcessor(query, None, requestQueue)
  override def produceSearchSaver: Actor = new SearchSaver with ActualSearchSaverComponents
}

class ActualDocumentSearcher(documentSetId: Long, query: String, requestQueue: ActorRef) extends DocumentSearcher(documentSetId, query, requestQueue) with ActualQueryProcessorFactory