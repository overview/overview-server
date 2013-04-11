package org.overviewproject.documentcloud

import org.overviewproject.documentcloud.DocumentRetrieverProtocol._
import org.overviewproject.http.{Credentials, PrivateRequest, PublicRequest}
import org.overviewproject.http.RequestQueueProtocol._
import org.overviewproject.test.ActorSystemContext
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

import akka.actor.Terminated
import akka.testkit.{TestActorRef, TestProbe}

class DocumentRetrieverSpec extends Specification {

  "DocumentRetriever" should {

    trait RetrievalSetup extends Scope {
      val documentUrl = "https://www.documentcloud.org/api/documents/id.txt"
    }

    trait PublicRetrievalSetup extends RetrievalSetup {
      val document = Document("id", "title", "public", "http://canonical-url")
    }

    trait PrivateRetrievalSetup extends RetrievalSetup {
      val document = Document("id", "title", "private", "http://canonical-url")
      val credentials = Credentials("user@host", "dcpassword")
    }

    abstract class PublicRetrievalContext extends ActorSystemContext with PublicRetrievalSetup
    abstract class PrivateRetrievalContext extends ActorSystemContext with PrivateRetrievalSetup

    "queue a request for a public url" in new PublicRetrievalContext {
      val recipient = TestProbe()
      val retriever = TestActorRef(new DocumentRetriever(document, recipient.ref, testActor, None))

      retriever ! Start()

      expectMsg(AddToEnd(PublicRequest(documentUrl)))
    }

    "send successful retrieval to recipient" in new PublicRetrievalContext {
      val text = "document text"
      val successfulResponse = TestSimpleResponse(200, text)
      val recipient = TestProbe()
      val retriever = TestActorRef(new DocumentRetriever(document, recipient.ref, testActor, None))

      retriever ! Result(successfulResponse)
      recipient.expectMsg(GetTextSucceeded(document, text))
    }
    
    "die after successful retrieval" in new PublicRetrievalContext {
      val text = "document text"
      val successfulResponse = TestSimpleResponse(200, text)
      val monitor = TestProbe()
      val recipient = TestProbe()
      val retriever = TestActorRef(new DocumentRetriever(document, recipient.ref, testActor, None))
      
      monitor watch retriever
      retriever ! Result(successfulResponse)
      
      monitor.expectMsgType[Terminated]
    }

    "put request for a private url in front of the queue" in new PrivateRetrievalContext {
      val recipient = TestProbe()
      val retriever = TestActorRef(new DocumentRetriever(document, recipient.ref, testActor, Some(credentials)))

      retriever ! Start()

      expectMsg(AddToFront(PrivateRequest(documentUrl, credentials)))
    }

    "handle redirect from a private document request" in new PrivateRetrievalContext {
      val redirectUrl = "https://someting.s3.amazon.com/doc.txt"
      val response = TestSimpleResponse(302, "ignored body", Map(("Location" -> redirectUrl)))
      val recipient = TestProbe()
      val retriever = TestActorRef(new DocumentRetriever(document, recipient.ref, testActor, Some(credentials)))

      retriever ! Start()
      expectMsg(AddToFront(PrivateRequest(documentUrl, credentials)))
      retriever ! Result(response)
      expectMsg(AddToEnd(PublicRequest(redirectUrl)))
    }
  }
}