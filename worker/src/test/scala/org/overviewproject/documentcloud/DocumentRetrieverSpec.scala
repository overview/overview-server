package org.overviewproject.documentcloud

import org.overviewproject.documentcloud.DocumentRetrieverProtocol._
import org.overviewproject.http.{ Credentials, PrivateRequest, PublicRequest }
import org.overviewproject.http.RequestQueueProtocol._
import org.overviewproject.test.{ ActorSystemContext, TestSimpleResponse }
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import akka.actor.Terminated
import akka.testkit.{ TestActorRef, TestProbe }
import org.specs2.mutable.Before

class DocumentRetrieverSpec extends Specification {

  "DocumentRetriever" should {

    trait RetrievalSetup extends Scope {
      val documentUrl = "https://www.documentcloud.org/api/documents/id.txt"
      val document: Document
      val credentials: Option[Credentials] = None
    }

    trait PublicRetrievalSetup extends RetrievalSetup {
      override val document = Document("id", "title", "public", "http://canonical-url")
    }

    trait SuccessfulResponse {
      val text = "document text"
      val successfulResponse = TestSimpleResponse(200, text)
    }
    
    trait FailedResponse {
      val header = "some-header"
      val value = "header-value"
      val headersDisplay = s"$header:$value"
      val statusCode = 404
      val text = "Not Found"
      val failedResponse = TestSimpleResponse(statusCode, text, Map((header -> value)))
    }
    
    trait PrivateRetrievalSetup extends RetrievalSetup {
      override val document = Document("id", "title", "private", "http://canonical-url")
      override val credentials = Some(Credentials("user@host", "dcpassword"))
    }

    abstract class RetrievalContext extends ActorSystemContext with Before {
      this: RetrievalSetup =>
      var recipient: TestProbe = _
      var retriever: TestActorRef[DocumentRetriever] = _

      def before = {
        recipient = TestProbe()
        retriever = TestActorRef(new DocumentRetriever(document, recipient.ref, testActor, credentials))
      }
    }

    abstract class PublicRetrievalContext extends RetrievalContext with PublicRetrievalSetup
    abstract class PrivateRetrievalContext extends RetrievalContext with PrivateRetrievalSetup

    "queue a request for a public url" in new PublicRetrievalContext {

      retriever ! Start()

      expectMsg(AddToEnd(PublicRequest(documentUrl)))
    }

    "send successful retrieval to recipient" in new PublicRetrievalContext with SuccessfulResponse {
      retriever ! Result(successfulResponse)
      recipient.expectMsg(GetTextSucceeded(document, text))
    }

    "send failed retrieval to recipient" in new PublicRetrievalContext with FailedResponse {

      retriever ! Result(failedResponse)
      recipient.expectMsg(GetTextFailed(document, text, Some(statusCode), Some(headersDisplay)))
    }

    "die after successful retrieval" in new PublicRetrievalContext with SuccessfulResponse {
      val monitor = TestProbe()

      monitor watch retriever
      retriever ! Result(successfulResponse)

      monitor.expectMsgType[Terminated]
    }

    "die after failed retrieval" in new PublicRetrievalContext with FailedResponse {
      val monitor = TestProbe()
      
      monitor watch retriever
      retriever ! Result(failedResponse)
      
      monitor.expectMsgType[Terminated]
    }
    
    "put request for a private url in front of the queue" in new PrivateRetrievalContext {
      retriever ! Start()

      expectMsg(AddToFront(PrivateRequest(documentUrl, credentials.get)))
    }

    "handle redirect from a private document request" in new PrivateRetrievalContext {
      val redirectUrl = "https://someting.s3.amazon.com/doc.txt"
      val response = TestSimpleResponse(302, "ignored body", Map(("Location" -> redirectUrl)))

      retriever ! Start()
      expectMsg(AddToFront(PrivateRequest(documentUrl, credentials.get)))
      retriever ! Result(response)
      expectMsg(AddToEnd(PublicRequest(redirectUrl)))
    }
  }
}