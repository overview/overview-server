package org.overviewproject.documentcloud

import org.specs2.mutable.Specification
import org.overviewproject.test.ActorSystemContext
import akka.testkit.TestActorRef
import org.overviewproject.documentcloud.DocumentRetrieverProtocol.GetTextSucceeded
import scala.concurrent.Promise


class DocumentReceiverSpec extends Specification {

  "DocumentReceiver" should {

    "call callback when receiving documents" in new ActorSystemContext {
      val document = Document("id", "title", "public", "http://canonical-url")
      val text = "document text"
      def callback(d: Document, t: String): Unit = testActor ! t
      val retrievalDone = Promise[Int]
      val receiver = TestActorRef(new DocumentReceiver(callback, 5, retrievalDone))

      receiver ! GetTextSucceeded(document, text)

      expectMsg(text)
    }

    "complete future when specified number of documents have been processed" in new ActorSystemContext {
      val document = Document("id", "title", "public", "http://canonical-url")
      val text = "document text"
      def callback(d: Document, t: String): Unit = {}

      val retrievalDone = Promise[Int]
      val receiver = TestActorRef(new DocumentReceiver(callback, 2, retrievalDone))

      receiver ! GetTextSucceeded(document, text)
      retrievalDone.isCompleted must beFalse

      receiver ! GetTextSucceeded(document, text)
      retrievalDone.isCompleted must beTrue
      
    }
  }
}