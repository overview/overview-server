package com.overviewdocs.documentcloud

import akka.actor._
import akka.testkit.{ TestActorRef, TestProbe }
import org.specs2.mutable.Before
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.time.NoTimeConversions
import scala.concurrent.duration._

import com.overviewdocs.documentcloud.DocumentRetrieverProtocol._
import com.overviewdocs.http.{Credentials,Request,Response}
import com.overviewdocs.http.RequestQueueProtocol._
import com.overviewdocs.test.ActorSystemContext

class DocumentRetrieverSpec extends Specification with NoTimeConversions {

  "DocumentRetriever" should {

    trait RetrievalSetup extends Scope {
      val documentUrl = "https://www.documentcloud.org/api/documents/id.txt"
      val document: Document
      val credentials: Option[Credentials] = None
    }

    trait PublicRetrievalSetup extends RetrievalSetup {
      override val document = Document("id", "title", 1, "public", documentUrl, "http://pageurl")
    }

    trait SuccessfulResponse {
      val text = "document text"
      val successfulResponse = Response(200, Map(), text.getBytes)
    }
    
    trait FailedResponse {
      val header = "some-header"
      val value = "header-value"
      val statusCode = 404
      val text = "Not Found"
      val failedResponse = Response(statusCode, Map(header -> Seq(value)), text.getBytes)
    }
    
    trait PrivateRetrievalSetup extends RetrievalSetup {
      override val document = Document("id", "title", 1, "private", documentUrl, "http://pageurl")
      override val credentials = Some(Credentials("user@host", "dcpassword"))
    }

    abstract class RetrievalContext extends ActorSystemContext with Before {
      this: RetrievalSetup =>
      var recipient: TestProbe = _
      var retriever: TestActorRef[DocumentRetriever] = _

      def before = {
        recipient = TestProbe()
        retriever = TestActorRef(Props(new DocumentRetriever(document, recipient.ref, testActor, credentials)), testActor, "retriever")
      }
    }

    abstract class PublicRetrievalContext extends RetrievalContext with PublicRetrievalSetup
    abstract class PrivateRetrievalContext extends RetrievalContext with PrivateRetrievalSetup
    
    "queue a request for a public url" in new PublicRetrievalContext {
      retriever ! Start()
      expectMsg(AddToFront(Request(documentUrl)))
    }

    "send successful retrieval to recipient" in new PublicRetrievalContext with SuccessfulResponse {
      retriever ! HttpSuccess(successfulResponse)
      recipient.expectMsg(GetTextSucceeded(document, text))
    }

    "send failed retrieval to recipient" in new PublicRetrievalContext with FailedResponse {
      retriever ! HttpSuccess(failedResponse)
      recipient.expectMsg(GetTextBadResponse(documentUrl, failedResponse))
    }

    "die after successful retrieval" in new PublicRetrievalContext with SuccessfulResponse {
      val monitor = TestProbe()

      monitor watch retriever
      retriever ! HttpSuccess(successfulResponse)

      monitor.expectMsgType[Terminated]
    }

    "die after failed retrieval" in new PublicRetrievalContext with FailedResponse {
      val monitor = TestProbe()
      
      monitor watch retriever
      retriever ! HttpSuccess(failedResponse)
      
      monitor.expectMsgType[Terminated]
    }
    
   "die after error" in new PublicRetrievalContext with FailedResponse {
      val monitor = TestProbe()
      
      monitor watch retriever
      retriever ! HttpFailure(new Throwable("error"))

      monitor.expectMsgType[Terminated]
    }

    "put request for a private url in back of the queue" in new PrivateRetrievalContext {
      retriever ! Start()

      expectMsg(AddToEnd(Request(documentUrl, credentials, false)))
    }

    "handle redirect from a private document request" in new PrivateRetrievalContext {
      val redirectUrl = "https://someting.s3.amazon.com/doc.txt"
      val response = Response(302, Map("Location" -> Seq(redirectUrl)), "ignored body".getBytes)

      retriever ! Start()
      expectMsg(AddToEnd(Request(documentUrl, credentials, false)))
      retriever ! HttpSuccess(response)
      expectMsg(AddToFront(Request(redirectUrl)))
    }
    
    "forward error to receiver" in new PublicRetrievalContext {
      val error = new Exception("something bad")
      
      retriever ! HttpFailure(error)
      
      recipient.expectMsg(GetTextException(documentUrl, error))
    }

    "send JobComplete to parent" in new PublicRetrievalContext with SuccessfulResponse {
      retriever ! HttpSuccess(successfulResponse)
      
      expectMsg(JobComplete())
    }
  }
}
