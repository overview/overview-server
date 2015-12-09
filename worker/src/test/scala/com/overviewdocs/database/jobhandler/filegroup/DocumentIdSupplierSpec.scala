package com.overviewdocs.jobhandler.filegroup

import akka.actor.{ActorRef,Props}
import akka.pattern.ask
import akka.testkit.TestProbe
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification

import com.overviewdocs.jobhandler.filegroup.DocumentIdSupplierProtocol._
import com.overviewdocs.test.ActorSystemContext
import com.overviewdocs.util.AwaitMethod

class DocumentIdSupplierSpec extends Specification with Mockito with AwaitMethod {
  sequential

  "DocumentIdSupplier" should {

    "reply to request for ids" in new DocumentIdSupplierScope {
      implicit val t = timeout
    
      val response = await(documentIdSupplier.ask(RequestIds(documentSetId, numberOfIds)))

      response must be_==(IdRequestResponse(documentIds))
    }

    "reply to multiple request for the same documentset" in new DocumentIdSupplierScope {
      implicit val t = timeout
          
      await(documentIdSupplier.ask(RequestIds(documentSetId, numberOfIds)))
      val nextResponse = await(documentIdSupplier.ask(RequestIds(documentSetId, numberOfIds)))

      nextResponse must be_==(IdRequestResponse(nextDocumentIds))
    }

    "reply to requests for different documentsets" in new DocumentIdSupplierScope {
      implicit val t = timeout
    
      await(documentIdSupplier.ask(RequestIds(documentSetId, numberOfIds)))
      val responseToo = await(documentIdSupplier.ask(RequestIds(documentSetIdToo, numberOfIds)))

      responseToo must be_==(IdRequestResponse(documentIdsToo))
    }
  }
  
  trait DocumentIdSupplierScope extends ActorSystemContext {
    val documentSetId = 1l
    val numberOfIds = 3

    val documentIds = Seq(1l, 2l, 3l)
    val nextDocumentIds = Seq(4l, 5l, 6l)

    val documentSetIdToo = 2l
    val documentIdsToo = Seq(7l, 8l)

    val documentIdSupplier: ActorRef = system.actorOf(Props(new TestDocumentIdSupplier))

    class TestDocumentIdSupplier extends DocumentIdSupplier {
      override protected def createDocumentIdGenerator(dsId: Long) = {
        val documentIdGenerator = smartMock[DocumentIdGenerator]

        dsId match {
          case `documentSetId` =>
            documentIdGenerator.nextIds(any) returns documentIds thenReturns nextDocumentIds
          case `documentSetIdToo` =>
            documentIdGenerator.nextIds(any) returns documentIdsToo
          case _ =>
            documentIdGenerator.nextIds(any) throws new RuntimeException("error")
        }
        documentIdGenerator

      }
    }
  }

}
