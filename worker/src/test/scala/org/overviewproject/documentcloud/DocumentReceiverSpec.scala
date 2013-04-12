package org.overviewproject.documentcloud

import scala.concurrent.duration._
import org.specs2.mutable.Specification
import org.overviewproject.test.ActorSystemContext
import akka.testkit.TestActorRef
import org.overviewproject.documentcloud.DocumentRetrieverProtocol._
import scala.concurrent.{Await, Promise}
import org.specs2.time.NoTimeConversions
import org.specs2.mutable.Before


class DocumentReceiverSpec extends Specification with NoTimeConversions {

  "DocumentReceiver" should {

    
    abstract class ReceiverContext extends ActorSystemContext with Before {
      val ExpectedDocuments = 2
      val document = Document("id", "title", "public", "http://canonical-url")
      val text = "document text"
      val url = "url"      
      
      var retrievalDone: Promise[Seq[DocumentRetrievalError]] = _
      var receiver: TestActorRef[DocumentReceiver] = _
  
      def  callback(d: Document, t: String): Unit = testActor ! t
      
      def before = {
    	retrievalDone = Promise[Seq[DocumentRetrievalError]]
    	receiver = TestActorRef(new DocumentReceiver(callback, ExpectedDocuments, retrievalDone))
      }
    }
    

    
    "call callback when receiving documents" in new ReceiverContext {
      receiver ! GetTextSucceeded(document, text)

      expectMsg(text)
    }
    
    "not call callback when retrieval failed" in new ReceiverContext {
      receiver ! GetTextFailed("failed url", text)

      expectNoMsg
    }

    "complete future when specified number of documents have been processed" in new ReceiverContext {
      receiver ! GetTextSucceeded(document, text)
      retrievalDone.isCompleted must beFalse

      receiver ! GetTextSucceeded(document, text)
      retrievalDone.isCompleted must beTrue
    }
    
    "return retrieval errors in future" in new ReceiverContext {
      receiver ! GetTextSucceeded(document, text)
      receiver ! GetTextFailed(url, text)

      retrievalDone.isCompleted must beTrue
      val retrievalErrors: Seq[DocumentRetrievalError] = Await.result(retrievalDone.future, 1 millis)
      retrievalErrors must haveTheSameElementsAs(Seq(DocumentRetrievalError(url, text)))
    }
    
  }
}