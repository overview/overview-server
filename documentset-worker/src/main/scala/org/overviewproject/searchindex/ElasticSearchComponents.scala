package org.overviewproject.searchindex

import org.overviewproject.jobhandler.SearcherComponents
import org.overviewproject.jobhandler.SearchIndex
import org.elasticsearch.index.query.QueryBuilders
import org.elasticsearch.node.NodeBuilder.nodeBuilder
import org.elasticsearch.action.search.SearchType
import org.elasticsearch.common.unit.TimeValue
import scala.concurrent.Promise
import org.elasticsearch.action.ActionListener
import scala.concurrent.Future
import org.elasticsearch.action.search.SearchResponse
import org.overviewproject.util.Logger

trait ElasticSearchComponents extends SearcherComponents {

  private class ActionResult[A] extends ActionListener[A] {

    private val p: Promise[A] = Promise()

    def resultFuture: Future[A] = p.future

    override def onResponse(response: A): Unit = {
      p.success(response)
    }

    override def onFailure(failure: Throwable): Unit = {
      p.failure(failure)
    }

  }
  class ElasticSearchIndex extends SearchIndex {

    private val client = ElasticSearchClient.client

    override def startSearch(index: String, queryString: String): Future[SearchResponse] = {
      Logger.debug(s"Starting query: $queryString")
      val query = QueryBuilders.multiMatchQuery(queryString, "text", "title")

      val listener = new ActionResult[SearchResponse]()

      client.prepareSearch(index)
        .setSearchType(SearchType.SCAN)
        .setScroll(new TimeValue(60000))
        .setQuery(query)
        .setSize(100)
        .execute(listener)

      listener.resultFuture
    }
    
    override def getNextSearchResultPage(scrollId: String): Future[SearchResponse] = {
      val listener = new ActionResult[SearchResponse]()
      
      client.prepareSearchScroll(scrollId)
        .setScroll(new TimeValue(60000))
        .execute(listener)
      
      listener.resultFuture
    }
  }

  override val searchIndex: SearchIndex = new ElasticSearchIndex
  
}