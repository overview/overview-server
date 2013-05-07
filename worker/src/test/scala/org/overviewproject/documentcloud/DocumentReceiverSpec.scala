package org.overviewproject.documentcloud

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration._
import scala.util.Failure

import org.overviewproject.documentcloud.DocumentReceiverProtocol.Done
import org.overviewproject.documentcloud.DocumentRetrieverProtocol.{GetTextError, GetTextFailed, GetTextSucceeded}
import org.overviewproject.test.ActorSystemContext
import org.specs2.mutable.{Before, Specification}
import org.specs2.time.NoTimeConversions

import akka.actor._
import akka.testkit.TestActorRef

class DocumentReceiverSpec extends Specification with NoTimeConversions {

  "DocumentReceiver" should {

    abstract class ReceiverContext extends ActorSystemContext with Before {
      val ExpectedDocuments = 2
      val document = Document("id", "title", 1, "public", "http://texturl", "http://pageurl")
      val text = "document text"
      val url = "url"      
      
      var retrievalDone: Promise[RetrievalResult] = _
      var receiver: TestActorRef[DocumentReceiver] = _
  
      def  callback(d: Document, t: String): Unit = testActor ! t
      
      def before = {
    	retrievalDone = Promise[RetrievalResult]
    	receiver = TestActorRef(new DocumentReceiver(callback, retrievalDone))
      }
    }
    
    abstract class CallbackError extends ReceiverContext {
      val error = new Exception("error")
      override def callback(d: Document, t: String): Unit = throw error
    }

    
    "call callback when receiving documents" in new ReceiverContext {
      receiver ! GetTextSucceeded(document, text)

      expectMsg(text)
    }
    
    "not call callback when retrieval failed" in new ReceiverContext {
      receiver ! GetTextFailed("failed url", text)

      expectNoMsg
    }

    "complete future when Done message is received" in new ReceiverContext {
      receiver ! Done(5, 5)
      retrievalDone.isCompleted must beTrue
    }
    

    "return retrieval results in future" in new ReceiverContext {
      val r = 5
      val t = 20
      
      receiver ! GetTextSucceeded(document, text)
      receiver ! GetTextFailed(url, text)
      receiver ! Done(r, t)
      
      retrievalDone.isCompleted must beTrue
      val retrievalResult: RetrievalResult = Await.result(retrievalDone.future, 1 millis)
      retrievalResult.failedRetrievals must haveTheSameElementsAs(Seq(DocumentRetrievalError(url, text)))
      retrievalResult.numberOfDocumentsRetrieved must be equalTo(r)
      retrievalResult.totalDocumentsInQuery must be equalTo(t)
    }
    
    "fail future in case of exceptions" in new ReceiverContext {
      val error = new Exception("error")
      
      receiver ! GetTextError(error)
      retrievalDone.isCompleted must beTrue
      
      retrievalDone.future.value must beSome(Failure(error))
    }
    
    "fail future if callback throws" in new CallbackError {
      receiver ! GetTextSucceeded(document, text)
      
      retrievalDone.isCompleted must beTrue
      retrievalDone.future.value must beSome(Failure(error))
    }
  }
}