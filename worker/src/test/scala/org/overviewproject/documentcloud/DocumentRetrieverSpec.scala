package org.overviewproject.documentcloud

import org.specs2.mutable.Specification
import org.overviewproject.test.ActorSystemContext
import akka.testkit.TestActorRef
import org.overviewproject.http.RequestQueueProtocol.AddToEnd
import org.overviewproject.http.PublicRequest

class DocumentRetrieverSpec extends Specification {

  "DocumentRetriever" should {
    
    "queue a request for a public url" in new ActorSystemContext {
      val document = Document("id", "title", "public", "http://canonical-url")
      val retriever = TestActorRef(new DocumentRetriever(testActor))
      
      retriever ! document
      
      expectMsg(AddToEnd(PublicRequest("url")))
    }
  }
}