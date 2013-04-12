package org.overviewproject.documentcloud

import scala.concurrent.duration._
import org.specs2.mutable.Specification
import org.overviewproject.test.ActorSystemContext
import akka.testkit.TestActorRef
import org.overviewproject.documentcloud.DocumentRetrieverProtocol._
import scala.concurrent.{Await, Promise}
import org.specs2.time.NoTimeConversions


class DocumentReceiverSpec extends Specification with NoTimeConversions {

  "DocumentReceiver" should {

    "call callback when receiving documents" in new ActorSystemContext {
      val document = Document("id", "title", "public", "http://canonical-url")
      val text = "document text"
      def callback(d: Document, t: String): Unit = testActor ! t
      val retrievalDone = Promise[Seq[DocumentRetrievalError]]
      val receiver = TestActorRef(new DocumentReceiver(callback, 5, retrievalDone))

      receiver ! GetTextSucceeded(document, text)

      expectMsg(text)
    }
    
    "not call callback when retrieval failed" in new ActorSystemContext {
      val document = Document("id", "title", "public", "http://canonical-url")
      val text = "Failure"
      def callback(d: Document, t: String): Unit = testActor ! t
      val retrievalDone = Promise[Seq[DocumentRetrievalError]]
      val receiver = TestActorRef(new DocumentReceiver(callback, 5, retrievalDone))

      receiver ! GetTextFailed("failed url", text)

      expectNoMsg
      
    }

    "complete future when specified number of documents have been processed" in new ActorSystemContext {
      val document = Document("id", "title", "public", "http://canonical-url")
      val text = "document text"
      def callback(d: Document, t: String): Unit = {}

      val retrievalDone = Promise[Seq[DocumentRetrievalError]]
      val receiver = TestActorRef(new DocumentReceiver(callback, 2, retrievalDone))

      receiver ! GetTextSucceeded(document, text)
      retrievalDone.isCompleted must beFalse

      receiver ! GetTextSucceeded(document, text)
      retrievalDone.isCompleted must beTrue
      
    }
    
    "return retrieval errors in future" in new ActorSystemContext {
      val document = Document("id", "title", "public", "http://canonical-url")
      val text = "document text"
      val errorText = "some failure"
      val failedUrl = "url"
      def callback(d: Document, t: String): Unit = {}
      
      val retrievalDone = Promise[Seq[DocumentRetrievalError]]
      val receiver = TestActorRef(new DocumentReceiver(callback, 2, retrievalDone))

      receiver ! GetTextSucceeded(document, text)
      receiver ! GetTextFailed(failedUrl, errorText)

      retrievalDone.isCompleted must beTrue
      val retrievalErrors: Seq[DocumentRetrievalError] = Await.result(retrievalDone.future, 1 millis)
      retrievalErrors must haveTheSameElementsAs(Seq(DocumentRetrievalError(failedUrl, errorText)))
    }
    
  }
}