package com.overviewdocs.documentcloud

import akka.actor._
import akka.testkit.TestActorRef
import scala.concurrent.{Await, Promise}
import scala.concurrent.duration._
import scala.util.Failure
import org.specs2.mutable.{Before, Specification}
import org.specs2.time.NoTimeConversions

import com.overviewdocs.documentcloud.DocumentReceiverProtocol.Done
import com.overviewdocs.documentcloud.DocumentRetrieverProtocol.{GetTextBadResponse, GetTextException, GetTextSucceeded}
import com.overviewdocs.http.Response
import com.overviewdocs.test.ActorSystemContext

class DocumentReceiverSpec extends Specification with NoTimeConversions {
  sequential

  "DocumentReceiver" should {

    abstract class ReceiverContext extends ActorSystemContext with Before {
      val ExpectedDocuments = 2
      val document = Document("id", "title", 1, "public", "http://texturl", "http://pageurl")
      val text = "document text"
      val url = "url"      
      
      var retrievalDone: Promise[RetrievalResult] = _
      var receiver: TestActorRef[DocumentReceiver] = _

      def textify(s: String) = s"textified $s"
  
      def  callback(d: Document, t: String): Unit = testActor ! t
      
      def before = {
    	retrievalDone = Promise[RetrievalResult]
    	receiver = TestActorRef(new DocumentReceiver(textify, callback, retrievalDone))
      }
    }
    
    abstract class CallbackError extends ReceiverContext {
      val error = new Exception("error")
      override def callback(d: Document, t: String): Unit = throw error
    }

    "call callback when receiving documents" in new ReceiverContext {
      receiver ! GetTextSucceeded(document, text)

      expectMsg("textified " + text)
    }
    
    "not call callback when retrieval failed" in new ReceiverContext {
      receiver ! GetTextBadResponse("failed url", Response(404, Map(), text.getBytes))

      expectNoMsg
    }

    "complete future when Done message is received" in new ReceiverContext {
      receiver ! Done(5, 5)
      retrievalDone.isCompleted must beTrue
    }
    

    "return bad responses" in new ReceiverContext {
      val r = 5
      val t = 20

      receiver ! GetTextBadResponse(url, Response(404, Map(), text.getBytes))
      receiver ! Done(r, t)
      
      val retrievalResult: RetrievalResult = Await.result(retrievalDone.future, 1 millis)
      retrievalResult.failedRetrievals must containTheSameElementsAs(Seq(DocumentRetrievalError(url, text, Some(404), Some(Map()))))
    }
    
    "fail future in case of exceptions" in new ReceiverContext {
      val error = new Exception("foo")

      receiver ! GetTextException(url, error)
      receiver ! Done(1, 2)

      val retrievalResult: RetrievalResult = Await.result(retrievalDone.future, 1 millis)
      retrievalResult.failedRetrievals must containTheSameElementsAs(Seq(DocumentRetrievalError(url, "foo", None, None)))
    }
  }
}
