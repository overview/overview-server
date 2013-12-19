package org.overviewproject.database.orm.finders

import org.overviewproject.database.orm.Schema
import org.overviewproject.test.DbSetup.insertDocumentSet
import org.overviewproject.test.DbSpecification
import org.overviewproject.tree.orm.SearchResult
import org.overviewproject.tree.orm.SearchResultState.InProgress
import org.overviewproject.tree.orm.stores.BaseStore

class SearchResultFinderSpec extends DbSpecification {

  step(setupDb)

  "SearchResultFinder" should {

    trait SearchResultContext extends DbTestContext {
      val searchResultFinder = SearchResultFinder()
      val query: String = "query terms"
      var documentSetId: Long = _
      var searchResult: SearchResult = _
      
      override def setupWithDb = {
        documentSetId = insertDocumentSet("SearchResultFinderSpec")
        searchResult = SearchResult(InProgress, documentSetId, query)

        BaseStore(Schema.searchResults).insertOrUpdate(searchResult)
      }
    }

    "find SearchResults by DocumentSet" in new SearchResultContext {
      val foundResult = searchResultFinder.byDocumentSet(documentSetId).headOption

      foundResult must beSome(searchResult)
    }

    "find SearchResults by DocumentSet and Query" in new SearchResultContext {
      val foundResult = searchResultFinder.byDocumentSetAndQuery(documentSetId, query).headOption

      foundResult must beSome(searchResult)
    }
  }

  step(shutdownDb)
}