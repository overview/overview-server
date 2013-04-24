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
      val document = Document("id", "title", 1, "public", "http://canonical-url")
      val text = "document text"
      val url = "url"      
      
      var retrievalDone: Promise[Seq[DocumentRetrievalError]] = _
      var receiver: TestActorRef[DocumentReceiver] = _
  
      def  callback(d: Document, t: String): Unit = testActor ! t
      
      def before = {
    	retrievalDone = Promise[Seq[DocumentRetrievalError]]
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
      receiver ! Done()
      retrievalDone.isCompleted must beTrue
    }
    

    "return retrieval errors in future" in new ReceiverContext {
      receiver ! GetTextSucceeded(document, text)
      receiver ! GetTextFailed(url, text)
      receiver ! Done()
      
      retrievalDone.isCompleted must beTrue
      val retrievalErrors: Seq[DocumentRetrievalError] = Await.result(retrievalDone.future, 1 millis)
      retrievalErrors must haveTheSameElementsAs(Seq(DocumentRetrievalError(url, text)))
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