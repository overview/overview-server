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
  case object WaitingForSearch extends State
  case object WaitingForSave extends State

  sealed trait Data
  case object Uninitialized extends Data
  case class Search(searchId: Long) extends Data
}

trait SearchIndexComponent {
  def searchForIds(documentSetId: Long, query: String): Future[Seq[Long]]
  def removeDocumentSet(documentSetId: Long): Future[Unit]
}

trait SearcherComponents {
  val searchIndex: SearchIndexComponent
  def produceSearchSaver: Actor = new SearchSaver with ActualSearchSaverComponents
}

trait SearchIndexSearcher extends Actor with FSM[State, Data] with SearcherComponents {
  import SearchIndexSearcherProtocol._
  import SearchSaverProtocol._
  import context.dispatcher

  private case class SearchResult(ids: Seq[Long])

  private val searchSaver = context.actorOf(Props(produceSearchSaver))

  startWith(Idle, Uninitialized)

  when(Idle) {
    case Event(StartSearch(searchId, documentSetId, query), _) =>
      startSearching(documentSetId, query)
      goto(WaitingForSearch) using Search(searchId)
  }

  when(WaitingForSearch) {
    case Event(SearchResult(ids), Search(searchId)) => {
      startSaving(searchId, ids)
      goto(WaitingForSave) using Search(searchId)
    }
  }

  when(WaitingForSave) {
    case Event(Terminated(a), _) => {
      context.parent ! SearchComplete
      stop
    }
  }

  initialize

  private def startSearching(documentSetId: Long, query: String): Unit = {
    searchIndex.searchForIds(documentSetId, query)
      .onComplete(_ match {
        case Success(ids) => self ! SearchResult(ids)
        case Failure(t) => {
          context.parent ! SearchFailure(t)
          context.stop(self)
        }
      })
  }

  private def startSaving(searchId: Long, ids: Seq[Long]): Unit = {
    if (ids.nonEmpty) {
      searchSaver ! SaveIds(searchId, ids)
    }
    context.watch(searchSaver)
    searchSaver ! PoisonPill
  }
}
