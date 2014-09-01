package org.overviewproject.searchindex

import org.overviewproject.jobhandler.documentset.SearchIndexComponent
import org.overviewproject.jobhandler.documentset.SearcherComponents

trait ElasticSearchComponents extends SearcherComponents {
  class ElasticSearchIndex extends SearchIndexComponent {
    private val client = ElasticSearchClient.client

    override def searchForIds(documentSetId: Long, query: String) = client.searchForIds(documentSetId, query)
    override def removeDocumentSet(documentSetId: Long) = client.removeDocumentSet(documentSetId)
  }

  override val searchIndex: SearchIndexComponent = new ElasticSearchIndex
}
