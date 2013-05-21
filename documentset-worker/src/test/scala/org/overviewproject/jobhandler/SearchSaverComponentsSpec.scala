package org.overviewproject.jobhandler

import org.specs2.mutable.Specification
import org.overviewproject.test.DbSetup._
import org.overviewproject.test.DbSpecification
import org.overviewproject.database.orm.stores.SearchResultStore
import org.overviewproject.database.orm.SearchResult
import org.overviewproject.database.orm.SearchResultState._
import org.overviewproject.documentcloud.Document
import org.overviewproject.database.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._

class SearchSaverComponentsSpec extends DbSpecification {
  step(setupDb)

  "SearchSaverComponent" should {

    "save DocumentSearchResults" in new DbTestContext {
      val documentSetId = insertDocumentSet("SearchSaverComponentSpec")
      val documentIds = insertDocuments(documentSetId, 10)
      val searchResult = SearchResultStore.insertOrUpdate(SearchResult(InProgress, documentSetId, "query"))
      val documentCloudDocuments = Seq.tabulate(10)(n =>
        Document(s"documentCloudId-${n + 1}", "title", 1, "public", "textUrl", "pageUrl"))

      val searchSaverComponent = new SearchSaverComponents {
        override val storage: Storage = new Storage
      }
      
      searchSaverComponent.storage.storeDocuments(searchResult.id, documentSetId, documentCloudDocuments)
      
      
      val storedDocumentIds = 
        from(Schema.documentSearchResults)(dsr => select(dsr.documentId))
      
      storedDocumentIds.toSeq must haveTheSameElementsAs(documentIds)

    }
  }

  step(shutdownDb)
}