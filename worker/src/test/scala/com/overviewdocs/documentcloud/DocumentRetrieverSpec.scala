package com.overviewdocs.documentcloud

import akka.actor.{ActorSystem,Props,Terminated}
import akka.testkit.{ImplicitSender,TestActorRef,TestKit,TestKitBase,TestProbe}
import org.specs2.mock.Mockito
import org.specs2.mutable.After
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import scala.concurrent.Future
import scala.util.Failure

import com.overviewdocs.documentcloud.DocumentRetrieverProtocol.{Start,GetTextSucceeded,GetTextFailed,JobComplete}
import com.overviewdocs.http.{Client=>HttpClient,Credentials,Response=>HttpResponse}
import com.overviewdocs.test.ActorSystemContext

// Declared here because of https://issues.scala-lang.org/browse/SI-4683
// See https://groups.google.com/d/msg/specs2-users/UYw2_hsxR4Q/2fqpeIBA_c8J
trait DocumentRetrieverSpecBaseScope extends Scope with TestKitBase with ImplicitSender with After with Mockito {
  val credentials: Option[Credentials] = None
  val documentUrl = "https://www.documentcloud.org/api/documents/id.txt"
  val document = Document("id", "title", 1, "public", documentUrl, "http://pageurl")

  val mockHttpClient = smartMock[HttpClient]
  val successfulResponse = HttpResponse(200, "text".getBytes, Map("foo" -> Seq("bar")))
  val failedResponse = HttpResponse(404, "not found".getBytes, Map("foo" -> Seq("bar")))
  val redirectResponse = HttpResponse(302, "see other".getBytes, Map("Location" -> Seq("http://example2.org")))

  implicit lazy val system: ActorSystem = ActorSystem()
  lazy val recipient: TestProbe = TestProbe()
  lazy val retriever: TestActorRef[DocumentRetriever] = TestActorRef(
    Props(new DocumentRetriever(document, recipient.ref, mockHttpClient, credentials)), testActor, "retriever"
  )

  override def after = system.shutdown
}

class DocumentRetrieverSpec extends Specification {
  "DocumentRetriever" should {

    "send successful retrieval to recipient" in new DocumentRetrieverSpecBaseScope {
      mockHttpClient.get(any)(any) returns Future.successful(successfulResponse)

      retriever ! Start()
      recipient.expectMsg(GetTextSucceeded(document, "text"))
      expectMsg(JobComplete())
    }

    "send non-200 HTTP response to recipient" in new DocumentRetrieverSpecBaseScope {
      mockHttpClient.get(any)(any) returns Future.successful(failedResponse)

      retriever ! Start()
      recipient.expectMsg(GetTextFailed(
        documentUrl,
        new String(failedResponse.bodyBytes, "utf-8"),
        Some(failedResponse.statusCode),
        Some(failedResponse.headers))
      )
      expectMsg(JobComplete())
    }

    "send HTTP error to recipient" in new DocumentRetrieverSpecBaseScope {
      mockHttpClient.get(any)(any) returns Future.failed(new Exception("foo"))

      retriever ! Start()
      recipient.expectMsg(GetTextFailed(documentUrl, "foo", None, None))
      expectMsg(JobComplete())
    }

    "die after successful retrieval" in new DocumentRetrieverSpecBaseScope {
      mockHttpClient.get(any)(any) returns Future.successful(successfulResponse)

      val monitor = TestProbe()
      monitor watch retriever
      retriever ! Start()
      monitor.expectMsgType[Terminated]
    }

    "die after failed retrieval" in new DocumentRetrieverSpecBaseScope {
      mockHttpClient.get(any)(any) returns Future.successful(failedResponse)

      val monitor = TestProbe()
      monitor watch retriever
      retriever ! Start()
      monitor.expectMsgType[Terminated]
    }
    
    "die after error" in new DocumentRetrieverSpecBaseScope {
      mockHttpClient.get(any)(any) returns Future.failed(new Exception("foo"))
      
      val monitor = TestProbe()
      monitor watch retriever
      retriever ! Start()
      monitor.expectMsgType[Terminated]
    }

    "handle redirect from a private document request" in new DocumentRetrieverSpecBaseScope {
      override val document = Document("id", "title", 1, "private", documentUrl, "http://pageurl")
      override val credentials = Some(Credentials("user@host", "dcpassword"))

      mockHttpClient.get(any, any, any)(any) returns Future.successful(redirectResponse)
      mockHttpClient.get(any)(any) returns Future.successful(successfulResponse)

      retriever ! Start()
      recipient.expectMsg(GetTextSucceeded(document, "text"))
      expectMsg(JobComplete())
    }
  }
}
