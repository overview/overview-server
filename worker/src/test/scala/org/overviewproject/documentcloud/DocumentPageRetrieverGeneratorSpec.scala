package org.overviewproject.documentcloud

import scala.concurrent.duration._

import org.overviewproject.documentcloud.DocumentRetrieverProtocol._
import org.overviewproject.test.ActorSystemContext
import org.specs2.mock.Mockito
import org.specs2.mutable.{Before, Specification}
import org.specs2.time.NoTimeConversions

import akka.actor._
import akka.testkit.TestActorRef

class DocumentPageRetrieverGeneratorSpec extends Specification with Mockito with NoTimeConversions {

  "DocumentPageRetrieverGenerator" should {

    case class Create(searchResult: SearchResult, receiver: ActorRef)

    class Wrapper(retrieverGenerator: RetrieverGenerator) extends Actor {
      def receive = {
        case Create(searchResult, receiver) => retrieverGenerator.createRetrievers(searchResult, receiver)
      }
    }

    val RetrieverReport: String = "[Retriever started]"

    class ReportingRetrieverFactory(listener: ActorRef) extends RetrieverFactory {
      override def produce(document: Document, receiver: ActorRef): Actor = new Actor {
        def receive = {
          case Start() => listener ! RetrieverReport
        }
      }
    }

    abstract class RetrieverContext extends ActorSystemContext with Before {
      val numberOfPages = 100
      val maxDocuments = 10
      val totalDocuments = 8
      
      var searchResult: SearchResult = _
      var wrapper: ActorRef = _
      var retrieverGenerator: RetrieverGenerator = _
      
      override def before = {
        val document = Document("id", "title", numberOfPages, "public", "textUrl", "pageUrlTemplate")

        searchResult = mock[SearchResult]
        searchResult.total returns totalDocuments
        searchResult.documents returns Seq.fill(5)(document)

        val retrieverFactory = new ReportingRetrieverFactory(testActor)

        retrieverGenerator = new DocumentPageRetrieverGenerator(retrieverFactory, maxDocuments)

        wrapper = TestActorRef(Props(new Wrapper(retrieverGenerator)))
      }
    }

    "spawn retrievers for each document" in new RetrieverContext {
      wrapper ! Create(searchResult, testActor)

      expectMsg(RetrieverReport)
    }

    "only spawn retriever for document if total page count does not exceed maxDocuments" in new RetrieverContext {
      wrapper ! Create(searchResult, testActor)

      expectMsg(RetrieverReport)
      expectNoMsg(100 millis)
    }
    
    "adjust documentsToRetrieve to only include documents until total page count reaches maxDocuments" in new RetrieverContext {
       wrapper ! Create(searchResult, testActor)	
       
       retrieverGenerator.documentsToRetrieve must be equalTo(1)
       retrieverGenerator.totalDocuments must be equalTo(totalDocuments)
    }
    
    "report morePagesAvailable only more documents need to be retrieved" in new RetrieverContext {
      wrapper ! Create(searchResult, testActor)	
      
      retrieverGenerator.morePagesAvailable must beFalse
    }
  }
}