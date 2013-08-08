package org.overviewproject.searchindex

import scala.concurrent.{Future, Promise}

import org.elasticsearch.action.ActionListener
import org.elasticsearch.action.search.{ SearchResponse, SearchType }
import org.elasticsearch.common.unit.TimeValue
import org.elasticsearch.index.query.QueryBuilders

import org.overviewproject.jobhandler.SearchIndex

import org.overviewproject.jobhandler.SearcherComponents
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
   
    private val PageSize = 100
    private val ScrollTime = new TimeValue(60000)
    private val SearchableFields = Seq("text", "title", "supplied_id")
    
    private val client = ElasticSearchClient.client

    override def startSearch(index: String, queryString: String): Future[SearchResponse] = {
      Logger.debug(s"Starting query: $queryString")
      val query = QueryBuilders.multiMatchQuery(queryString, SearchableFields: _*)

      val listener = new ActionResult[SearchResponse]()

      client.prepareSearch(index)
        .setSearchType(SearchType.SCAN)
        .setScroll(ScrollTime)
        .setQuery(query)
        .setSize(PageSize)
        .execute(listener)

      listener.resultFuture
    }
    
    override def getNextSearchResultPage(scrollId: String): Future[SearchResponse] = {
      val listener = new ActionResult[SearchResponse]()
      
      client.prepareSearchScroll(scrollId)
        .setScroll(ScrollTime)
        .execute(listener)
      
      listener.resultFuture
    }
  }

  override val searchIndex: SearchIndex = new ElasticSearchIndex
  
}