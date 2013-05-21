package org.overviewproject.jobhandler

import akka.actor.ActorContext
import org.overviewproject.database.orm.finders.SearchResultFinder
import akka.actor.Actor
import akka.actor.Props
import akka.actor.ActorRef
import org.overviewproject.database.orm.stores.SearchResultStore

object SearchHandlerProtocol {
  case class Search(documentSetId: Long, query: String, requestQueue: ActorRef)
}

trait SearchHandlerComponents {
  val storage: Storage
  val actorCreator: ActorCreator

  class Storage {
    import org.overviewproject.database.orm.{ SearchResult, SearchResultState }

    def searchExists(documentSetId: Long, query: String): Boolean =
      SearchResultFinder.byDocumentSetAndQuery(documentSetId, query).headOption.isDefined

    def createSearchResult(documentSetId: Long, query: String): Long = {
      val searchResult = SearchResultStore.insertOrUpdate(
        SearchResult(SearchResultState.InProgress, documentSetId, query))

      searchResult.id
    }

  }

  class ActorCreator {
    def produceDocumentSearcher(documentSetId: Long, query: String, requestQueue: ActorRef): Actor =
      new DocumentSearcher(documentSetId, query, requestQueue) with ActualQueryProcessorFactory
  }
}

trait SearchHandler extends Actor {
  this: SearchHandlerComponents =>

  import SearchHandlerProtocol._
  import DocumentSearcherProtocol._

  def receive = {
    case Search(documentSetId, query, requestQueue) => search(documentSetId, query, requestQueue)
  }

  private def search(documentSetId: Long, query: String, requestQueue: ActorRef): Unit = {
    if (storage.searchExists(documentSetId, query)) context.parent ! Done
    else startSearch(documentSetId, query: String, requestQueue)
  }

  private def startSearch(documentSetId: Long, query: String, requestQueue: ActorRef): Unit = {
    val searchId = storage.createSearchResult(documentSetId, query)
    val documentSearcher =
      context.actorOf(Props(actorCreator.produceDocumentSearcher(documentSetId, query, requestQueue)))

    documentSearcher ! StartSearch(searchId)
  }

}
