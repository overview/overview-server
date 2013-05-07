package org.overviewproject.documentcloud

import scala.concurrent.{Await, Promise}
import scala.concurrent.duration._

import org.overviewproject.documentcloud.DocumentRetrieverManagerProtocol._
import org.overviewproject.documentcloud.DocumentRetrieverProtocol._
import org.overviewproject.test.ActorSystemContext
import org.specs2.mock.Mockito
import org.specs2.mutable.{Before, Specification}
import org.specs2.time.NoTimeConversions

import akka.actor._
import akka.testkit.{TestActorRef, TestProbe}


class DocumentRetrieverManagerSpec extends Specification with Mockito with NoTimeConversions {

  "DocumentRetrieverManager" should {

    abstract class RetrieverContext extends ActorSystemContext with Before {
      var document: Document = _
      var searchResult: SearchResult = _
       
      def processDocuments(document: Document, text: String): Unit = {}
      val maxDocuments = 5
      val totalDocuments = 10
      
      val RetrieverReport: String = "[Retriever started]"

      class ReportingRetrieverFactory(listener: ActorRef) extends RetrieverFactory {
        override def produce(document: Document, receiver: ActorRef): Actor = new Actor {
          def receive = {
            case Start() => listener ! RetrieverReport
          }
        }
      }

      var retrieverListener: TestProbe = _
      var retrieverGenerator: DocumentRetrieverGenerator = _

      override def before = {
        retrieverGenerator = mock[DocumentRetrieverGenerator]
        document = mock[Document]
        searchResult = mock[SearchResult].smart
        searchResult.total returns totalDocuments
        searchResult.page returns 1
        searchResult.documents returns (Seq(document))

        retrieverListener = TestProbe()
      }
      
      val importResult = Promise[RetrievalResult]
      def createManager = TestActorRef(Props(new DocumentRetrieverManager(retrieverGenerator, processDocuments, importResult, maxDocuments)),
        testActor, "DocumentRetrieverManager")
    }

    "start retrievers for documents in result" in new RetrieverContext {
      val manager = createManager
      manager ! Retrieve(searchResult)
      
      there was one(retrieverGenerator).createRetrievers(any[SearchResult], any[ActorRef])(any[ActorContext])
    }

    "request next page of search results" in new RetrieverContext {
      val manager = createManager
      searchResult.total returns 3
      searchResult.documents returns Seq.fill(2)(document)
      retrieverGenerator.morePagesAvailable returns true
      
      manager ! Retrieve(searchResult)

      expectMsg(GetSearchResultPage(2))
    }
    
    "don't request more pages if none are available" in new RetrieverContext {
       searchResult.documents returns (Seq.fill(2 * maxDocuments)(document))
       retrieverGenerator.morePagesAvailable returns false
       
       val manager = createManager
       manager ! Retrieve(searchResult)
       
       expectNoMsg(100 millis)
    }
    
    "send DocumentRetrieved to parent when retriever completes" in new RetrieverContext {
      retrieverGenerator.documentsToRetrieve returns maxDocuments
      val manager = createManager
      
      ignoreMsg { 
        case GetSearchResultPage(n) => true 
      }
      
      manager ! Retrieve(searchResult)
      manager ! JobComplete()
      
      expectMsg(DocumentRetrieved(1, maxDocuments))
    }
    
    "complete importResult promise after all documents have been retrieved" in new RetrieverContext {
      val manager = createManager 
      searchResult.total returns 1
      retrieverGenerator.documentsToRetrieve returns 1
      retrieverGenerator.totalDocuments returns 1
      
      manager ! Retrieve(searchResult)
      manager ! JobComplete()
      
      awaitCond(importResult.isCompleted, 100 millis)
      val r = Await.result(importResult.future, 100 millis)
      r.numberOfDocumentsRetrieved must be equalTo(1)
      r.totalDocumentsInQuery must be equalTo(1)
    }
  }
}