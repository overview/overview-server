package org.overviewproject.jobhandler.documentset

import scala.collection.JavaConverters._
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}
import akka.actor._
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesResponse
import org.elasticsearch.action.deletebyquery.DeleteByQueryResponse
import org.elasticsearch.action.search.SearchResponse
import org.overviewproject.jobhandler.documentset.SearchIndexSearcherFSM._
import org.overviewproject.util.Logger

object SearchIndexSearcherProtocol {
  case class StartSearch(searchId: Long, documentSetId: Long, query: String)
  case object SearchComplete
  case class SearchFailure(e: Throwable)
}

object SearchIndexSearcherFSM {
  sealed trait State
  case object Idle extends State
  case object WaitingForSearchInfo extends State
  case object WaitingForSearchSaverEnd extends State

  sealed trait Data
  case object Uninitialized extends Data
  case class Search(searchId: Long) extends Data
}

trait SearchIndexComponent {
  def startSearch(index: String, query: String): Future[SearchResponse]
  def getNextSearchResultPage(scrollId: String): Future[SearchResponse]
  def deleteDocuments(documentSetId: Long): Future[DeleteByQueryResponse]
  def deleteDocumentSetAlias(documentSetId: Long): Future[IndicesAliasesResponse]
}

trait SearcherComponents {
  val searchIndex: SearchIndexComponent
  def produceSearchSaver: Actor = new SearchSaver with ActualSearchSaverComponents
}

trait SearchIndexSearcher extends Actor with FSM[State, Data] with SearcherComponents {

  import SearchIndexSearcherProtocol._
  import SearchSaverProtocol._
  import context.dispatcher
  

  private case class SearchInfo(scrollId: String)
  private case class SearchResult(result: SearchResponse)

  private val searchSaver = context.actorOf(Props(produceSearchSaver))

  startWith(Idle, Uninitialized)

  when(Idle) {
    case Event(StartSearch(searchId, documentSetId, query), _) =>
      getSearchInfo(documentSetId, query)
      goto(WaitingForSearchInfo) using Search(searchId)
  }

  when(WaitingForSearchInfo) {
    case Event(SearchInfo(scrollId), _) => {
      getNextSearchResultPage(scrollId)
      stay
    }
    case Event(SearchResult(r), Search(searchId)) if searchHasHits(r) => {
      saveAndContinueSearch(searchId, r)
      stay
    }
    case Event(SearchResult(r), Search(searchId)) => {
      waitForSavesToComplete
      goto(WaitingForSearchSaverEnd) using Search(searchId)
    }
  }

  when(WaitingForSearchSaverEnd) {
    case Event(Terminated(a), _) => {
      context.parent ! SearchComplete
      stop
    }
  }

  initialize

  private def getSearchInfo(documentSetId: Long, query: String): Unit = {
    val index = documentSetIndex(documentSetId)
    searchIndex.startSearch(index, query) onComplete handleSearchResult(r => SearchInfo(r.getScrollId))
  }

  private def getNextSearchResultPage(scrollId: String): Unit = 
    searchIndex.getNextSearchResultPage(scrollId) onComplete handleSearchResult(r => SearchResult(r))
  
  private def searchHasHits(result: SearchResponse): Boolean = result.getHits.hits.length > 0

  private def saveAndContinueSearch(searchId: Long, result: SearchResponse): Unit = {
    val ids: Array[Long] = for (hit <- result.getHits.hits) yield {
      hit.getSource.get("id").asInstanceOf[Long]
    }

    searchSaver ! SaveIds(searchId, ids)
    getNextSearchResultPage(result.getScrollId)
  }

  private def waitForSavesToComplete: Unit = {
    context.watch(searchSaver)
    searchSaver ! PoisonPill
  }

  private def handleSearchResult(message: SearchResponse => Any)(result: Try[SearchResponse]): Unit = result match {
    case Success(r) => self ! message(r)
    case Failure(t) => {
      context.parent ! SearchFailure(t)
      context.stop(self)
    }
  }

  private def documentSetIndex(documentSetId: Long): String = s"documents_$documentSetId"
}
