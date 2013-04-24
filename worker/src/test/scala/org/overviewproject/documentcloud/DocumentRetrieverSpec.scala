package org.overviewproject.documentcloud

import org.overviewproject.documentcloud.DocumentRetrieverProtocol._
import org.overviewproject.http.{ Credentials, PrivateRequest, PublicRequest }
import org.overviewproject.http.RequestQueueProtocol._
import org.overviewproject.test.{ ActorSystemContext, TestSimpleResponse }
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import akka.actor._
import akka.testkit.{ TestActorRef, TestProbe }
import org.specs2.mutable.Before
import scala.concurrent.duration._
import org.specs2.time.NoTimeConversions

class DocumentRetrieverSpec extends Specification with NoTimeConversions {

  "DocumentRetriever" should {

    trait RetrievalSetup extends Scope {
      val documentUrl = "https://www.documentcloud.org/api/documents/id.txt"
      val document: Document
      val credentials: Option[Credentials] = None
      val retryTimes: RequestRetryTimes = new RequestRetryTimes {
        override val times: Seq[FiniteDuration] = Seq()
      }
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

    trait ShortRetryTimes extends PublicRetrievalSetup {
      override val retryTimes = new RequestRetryTimes {
        override val times: Seq[FiniteDuration] = Seq(10 millis, 10 millis)
      }
    }
    
    abstract class RetrievalContext extends ActorSystemContext with Before {
      this: RetrievalSetup =>
      var recipient: TestProbe = _
      var retriever: TestActorRef[DocumentRetriever] = _

      def before = {
        recipient = TestProbe()
        retriever = TestActorRef(Props(new DocumentRetriever(document, recipient.ref, testActor, credentials, retryTimes)), testActor, "retriever")
      }
    }

    abstract class PublicRetrievalContext extends RetrievalContext with PublicRetrievalSetup
    abstract class PrivateRetrievalContext extends RetrievalContext with PrivateRetrievalSetup
    abstract class RetryContext extends RetrievalContext with ShortRetryTimes 
    
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
      recipient.expectMsg(GetTextFailed(documentUrl, text, Some(statusCode), Some(headersDisplay)))
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
    
   "die after error" in new PublicRetrievalContext with FailedResponse {
      val monitor = TestProbe()
      
      monitor watch retriever
      retriever ! Failure(new Throwable("error"))
      
      monitor.expectMsgType[Terminated]
    }

    "put request for a private url in front of the queue" in new PrivateRetrievalContext {
      retriever ! Start()

      expectMsg(AddToFront(PrivateRequest(documentUrl, credentials.get, false)))
    }

    "handle redirect from a private document request" in new PrivateRetrievalContext {
      val redirectUrl = "https://someting.s3.amazon.com/doc.txt"
      val response = TestSimpleResponse(302, "ignored body", Map(("Location" -> redirectUrl)))

      retriever ! Start()
      expectMsg(AddToFront(PrivateRequest(documentUrl, credentials.get, false)))
      retriever ! Result(response)
      expectMsg(AddToEnd(PublicRequest(redirectUrl)))
    }
    
    "forward error to receiver" in new PublicRetrievalContext {
      val error = new Error("something bad")
      
      retriever ! Failure(error)
      
      recipient.expectMsg(GetTextError(error))
    }
    
    "convert exception to failed document and forward to receiver" in new PublicRetrievalContext {
      val error = new Exception("document retrieveal exception")
      
      retriever ! Failure(error)
      
      recipient.expectMsg(GetTextFailed(documentUrl, error.toString(), None, None))
    }
    
    "retry if request completed but failed" in new RetryContext {
      val failedResponse = TestSimpleResponse(404, "Not found")
      val initialRequest = AddToEnd(PublicRequest(documentUrl))
      val retryRequest = AddToFront(PublicRequest(documentUrl))
      
      retriever ! Start()
      expectMsg(initialRequest)
      
      retriever ! Result(failedResponse)
      expectMsg(retryRequest)
      
      retriever ! Result(failedResponse)
      expectMsg(retryRequest)
      
      retriever ! Result(failedResponse)
      recipient.expectMsg(GetTextFailed(documentUrl, "Not found", Some(404), Some("")))
    }
    
    "retry on exceptions" in new RetryContext {
      val error = new Exception("probably recoverable")
      val request = AddToFront(PublicRequest(documentUrl))
      
      retriever ! Failure(error)
      expectMsg(request)
    }
    
    "send JobComplete to parent" in new PublicRetrievalContext with SuccessfulResponse {
      retriever ! Result(successfulResponse)
      
      expectMsg(JobComplete())
    }
  }
}