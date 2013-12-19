package org.overviewproject.jobhandler.documentset

import org.overviewproject.database.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.test.DbSetup._
import org.overviewproject.test.DbSpecification
import org.overviewproject.tree.orm.SearchResult
import org.overviewproject.tree.orm.SearchResultState._
import org.overviewproject.tree.orm.stores.BaseStore

class SearchSaverComponentsSpec extends DbSpecification {
  step(setupDb)

  "SearchSaverComponent" should {

    "save DocumentSearchResults" in new DbTestContext {
      val documentSetId = insertDocumentSet("SearchSaverComponentSpec")
      val documentIds = insertDocuments(documentSetId, 10)
      val searchResult = BaseStore(Schema.searchResults).insertOrUpdate(SearchResult(InProgress, documentSetId, "query"))

      val searchSaverComponent = new SearchSaverComponents {
        override val storage: Storage = new Storage
      }
      
      searchSaverComponent.storage.storeDocuments(searchResult.id, documentIds)
      
      
      val storedDocumentIds = 
        from(Schema.documentSearchResults)(dsr => select(dsr.documentId))
      
      storedDocumentIds.toSeq must haveTheSameElementsAs(documentIds)

    }
  }

  step(shutdownDb)
}