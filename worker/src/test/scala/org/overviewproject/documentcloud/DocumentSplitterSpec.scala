package org.overviewproject.documentcloud

import org.overviewproject.documentcloud.DocumentRetrieverProtocol.{JobComplete, Start}
import org.overviewproject.test.ActorSystemContext
import org.specs2.mutable.{Before, Specification}
import org.specs2.time.NoTimeConversions

import akka.actor._
import akka.testkit.{TestActorRef, TestProbe}


class DocumentSplitterSpec extends Specification with NoTimeConversions {

  
  class SilentActor extends Actor {
    def receive = {
      case _ =>
    }
  }
  
  "DocumentSplitter" should {
    
    trait DocumentSetup {
      val numberOfPages: Int = 2
    }
    
    
    abstract class DocumentSplitterContext extends ActorSystemContext with DocumentSetup with Before {

      def retrieverGenerator(documentRecorder: ActorRef, document: Document, receiver: ActorRef): Actor = {
        documentRecorder ! document
        new SilentActor
      }
      
      val document = Document("id", "title", numberOfPages, "public", "texturl", "page-{page}")
      val pages: Seq[Document] = Seq.tabulate(2)(n => new DocumentPage(document, n + 1))
      
      var documentSplitter: TestActorRef[DocumentSplitter] = _
      var documentRecorder: TestProbe = _
      
      def before = {
        val actorName = s"DocumentSplitter-${scala.util.Random.nextLong()}" // try to get a unique name for the actor
        documentRecorder = TestProbe()
        documentSplitter = 
          TestActorRef(Props(new DocumentSplitter(document, testActor, retrieverGenerator(documentRecorder.ref, _, _))),
              testActor, actorName)
      }
      
    }

    trait DocumentWithNoPages extends DocumentSetup {
      override val numberOfPages = 0
    }

    "create retriever actors for each page in the document" in new DocumentSplitterContext {
      documentSplitter ! Start()
      val documents = documentRecorder.receiveN(2)
      documents must haveTheSameElementsAs(pages)
    }

    "send JobComplete() to parent when page retrievers are finished" in new DocumentSplitterContext {
      documentSplitter ! Start()

      documentSplitter ! JobComplete()
      documentSplitter ! JobComplete()

      expectMsg(JobComplete())
    }
    
    "die after completion" in new DocumentSplitterContext {
      val monitor = TestProbe()
      monitor watch documentSplitter
      
      documentSplitter ! Start()
      documentSplitter ! JobComplete()
      documentSplitter ! JobComplete()

      monitor.expectMsgType[Terminated]
    }
    
    "send JobComplete to parent if document has no pages" in new DocumentSplitterContext with DocumentWithNoPages {
      documentSplitter ! Start()
      
      expectMsg(JobComplete())
    }
  }
}