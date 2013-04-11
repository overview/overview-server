package org.overviewproject.documentcloud

import org.overviewproject.http.{Credentials, PrivateRequest, PublicRequest}
import org.overviewproject.http.RequestQueueProtocol._
import org.overviewproject.test.ActorSystemContext
import org.specs2.mutable.Specification
import akka.testkit.TestActorRef
import org.overviewproject.http.PublicRequest

class DocumentRetrieverSpec extends Specification {

  "DocumentRetriever" should {
    
    "queue a request for a public url" in new ActorSystemContext {
      val document = Document("id", "title", "public", "http://canonical-url")
      val documentUrl = "https://www.documentcloud.org/api/documents/id.txt"
      val retriever = TestActorRef(new DocumentRetriever(testActor, None))
      
      retriever ! document
      
      expectMsg(AddToEnd(PublicRequest(documentUrl)))
    }
    
    "put request for a private url in front of the queue" in new ActorSystemContext {
      val credentials = Credentials("user@host", "dcpassword")
      val document = Document("id", "title", "private", "http://canonical-url")
      val documentUrl = "https://www.documentcloud.org/api/documents/id.txt"
      val retriever = TestActorRef(new DocumentRetriever(testActor, Some(credentials)))
      
      retriever ! document
      
      expectMsg(AddToFront(PrivateRequest(documentUrl, credentials)))
    }
    
    "handle redirect from a private document request" in new ActorSystemContext {
      val credentials = Credentials("user@host", "dcpassword")
      val document = Document("id", "title", "private", "http://canonical-url")
      val documentUrl = "https://www.documentcloud.org/api/documents/id.txt"
      val redirectUrl = "https://someting.s3.amazon.com/doc.txt"
      val response = TestSimpleResponse(302, "ignored body", Map(("Location" -> redirectUrl)))
      val retriever = TestActorRef(new DocumentRetriever(testActor, Some(credentials)))
      
      retriever ! document
      expectMsg(AddToFront(PrivateRequest(documentUrl, credentials)))
      retriever ! Result(response)      
      expectMsg(AddToEnd(PublicRequest(redirectUrl)))
    }
  }
}