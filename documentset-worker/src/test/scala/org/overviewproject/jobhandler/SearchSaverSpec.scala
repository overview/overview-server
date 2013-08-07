package org.overviewproject.jobhandler


import org.specs2.mutable.Specification
import org.overviewproject.test.ActorSystemContext
import org.specs2.mock.Mockito
import org.overviewproject.documentcloud.Document
import akka.testkit.TestActorRef
import org.overviewproject.jobhandler.SearchSaverProtocol.SaveIds


class SearchSaverSpec extends Specification with Mockito {

  "SearchSaver" should {
    
    class TestSearchSaver extends SearchSaver with SearchSaverComponents {
      val storage = mock[Storage]
    }
        
    
    "Store document ids with search result id" in new ActorSystemContext {
      val searchResultId = 4l
      val documentIds = Array[Long](1, 2, 3)
      
      val searchSaver = TestActorRef(new TestSearchSaver)
      
      searchSaver ! SaveIds(searchResultId, documentIds)
      
     there was one(searchSaver.underlyingActor.storage).storeDocuments(searchResultId, documentIds)
    }
  }
}