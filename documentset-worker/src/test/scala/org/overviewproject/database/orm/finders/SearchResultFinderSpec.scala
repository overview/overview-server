package org.overviewproject.database.orm.finders

import org.overviewproject.database.orm.SearchResult
import org.overviewproject.database.orm.SearchResultState.InProgress
import org.overviewproject.database.orm.stores.SearchResultStore
import org.overviewproject.test.DbSetup.insertDocumentSet
import org.overviewproject.test.DbSpecification
import org.specs2.mutable.Before

class SearchResultFinderSpec extends DbSpecification {

  step(setupDb)

  "SearchResultFinder" should {

    trait SearchResultContext extends DbTestContext {
      val query: String = "query terms"
      var documentSetId: Long = _
      var searchResult: SearchResult = _

      override def setupWithDb = {
        documentSetId = insertDocumentSet("SearchResultFinderSpec")
        searchResult = SearchResult(InProgress, documentSetId, query)

        SearchResultStore.insertOrUpdate(searchResult)
      }
    }
    
    "find SearchResults by DocumentSet" in new SearchResultContext {
      val foundResult = SearchResultFinder.byDocumentSet(documentSetId).headOption

      foundResult must beSome(searchResult)
    }
    
    "find SearchResults by DocumentSet and Query" in new SearchResultContext {
      val foundResult = SearchResultFinder.byDocumentSetAndQuery(documentSetId, query).headOption
      
      foundResult must beSome(searchResult)
    }
  }

  step(shutdownDb)
}