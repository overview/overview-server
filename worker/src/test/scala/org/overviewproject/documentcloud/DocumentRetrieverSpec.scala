package org.overviewproject.documentcloud

import org.overviewproject.http.{Credentials, PrivateRequest, PublicRequest}
import org.overviewproject.http.RequestQueueProtocol.AddToEnd
import org.overviewproject.test.ActorSystemContext
import org.specs2.mutable.Specification

import akka.testkit.TestActorRef

class DocumentRetrieverSpec extends Specification {

  "DocumentRetriever" should {
    
    "queue a request for a public url" in new ActorSystemContext {
      val document = Document("id", "title", "public", "http://canonical-url")
      val documentUrl = "https://www.documentcloud.org/api/documents/id.txt"
      val retriever = TestActorRef(new DocumentRetriever(testActor, None))
      
      retriever ! document
      
      expectMsg(AddToEnd(PublicRequest(documentUrl)))
    }
    
    "queue a request for a private url" in new ActorSystemContext {
      val credentials = Credentials("user@host", "dcpassword")
      val document = Document("id", "title", "private", "http://canonical-url")
      val documentUrl = "https://www.documentcloud.org/api/documents/id.txt"
      val retriever = TestActorRef(new DocumentRetriever(testActor, Some(credentials)))
      
      retriever ! document
      
      expectMsg(AddToEnd(PrivateRequest(documentUrl, credentials)))
      
    }
  }
}