package org.overviewproject.jobhandler


import org.specs2.mutable.Specification
import org.overviewproject.test.ActorSystemContext
import org.specs2.mock.Mockito
import org.overviewproject.documentcloud.Document
import akka.testkit.TestActorRef
import org.overviewproject.jobhandler.SearchSaverProtocol.Save


class SearchSaverSpec extends Specification with Mockito {

  "SearchSaver" should {
    
    class TestSearchSaver extends SearchSaver with SearchSaverComponents {
      val storage = mock[Storage]
      
      def storeDocumentsCalled(id: Long, documents: Iterable[Document]) =
        there was one(storage).storeDocuments(id, documents)
    }
        
    "Store documents with search result id" in new ActorSystemContext {
      val searchResultId = 4l
      val document = mock[Document]
      val documents = Seq.fill(10)(document)
      
      val searchSaver = TestActorRef(new TestSearchSaver)
      
      searchSaver ! Save(searchResultId, documents)
      
      searchSaver.underlyingActor.storeDocumentsCalled(searchResultId, documents) 
    }
  }
}