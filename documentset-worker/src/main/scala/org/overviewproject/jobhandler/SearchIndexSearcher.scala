package org.overviewproject.jobhandler

import akka.actor.Actor
import akka.actor.FSM
import org.overviewproject.jobhandler.SearchIndexSearcherFSM._
import scala.util.Success
import org.overviewproject.util.Logger
import scala.concurrent.Future
import org.elasticsearch.action.search.SearchResponse
import scala.util.Failure
import akka.actor._
import scala.collection.JavaConverters._
import scala.util.Try

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
  case class Search(searchId: Long, documentSetId: Long, query: String) extends Data
}

trait SearchIndex {
  def startSearch(index: String, query: String): Future[SearchResponse]
  def getNextSearchResultPage(scrollId: String): Future[SearchResponse]
}

trait SearcherComponents {
  val searchIndex: SearchIndex
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
      val f = searchIndex.startSearch(documentSetIndex(documentSetId), query)
      f onComplete handleSearchResult(r => SearchInfo(r.getScrollId))
      goto(WaitingForSearchInfo) using Search(searchId, documentSetId, query)
  }

  when(WaitingForSearchInfo) {
    case Event(SearchInfo(scrollId), _) => {
      searchIndex.getNextSearchResultPage(scrollId) onComplete handleSearchResult(r => SearchResult(r))
      stay
    }
    case Event(SearchResult(r), Search(searchId, _, _)) => {
      val ids: Array[Long] = for (hit <- r.getHits.hits) yield {
        val m = hit.getSource.asScala
        Logger.debug(m.keys.mkString("[", ",", "]"))
        Logger.debug(s"SOURCE ${m.get("id")}")
        
        hit.getSource.get("id").asInstanceOf[Long]
      } 

      if (ids.length > 0) {
        searchSaver ! SaveIds(searchId, ids)
        searchIndex.getNextSearchResultPage(r.getScrollId) onComplete handleSearchResult(r => SearchResult(r))

        stay
      } else {
        context.watch(searchSaver)
        searchSaver ! PoisonPill

        goto(WaitingForSearchSaverEnd) using Uninitialized
      }
    }
  }

  when(WaitingForSearchSaverEnd) {
    case Event(Terminated(a), _) => {
      context.parent ! SearchComplete
      stop
    }
  }

  initialize
  
  private def handleSearchResult(message: SearchResponse => Any)(result: Try[SearchResponse]): Unit = result match {
    case Success(r) => self ! message(r)
    case Failure(t) => {
      context.parent ! SearchFailure(t)
      context.stop(self)
    } 
  }

  private def documentSetIndex(documentSetId: Long): String = s"documents_$documentSetId"
}
