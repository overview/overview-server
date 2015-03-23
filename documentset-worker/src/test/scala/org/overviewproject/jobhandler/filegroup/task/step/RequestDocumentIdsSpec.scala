package org.overviewproject.jobhandler.filegroup.task.step

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.overviewproject.test.ActorSystemContext
import akka.testkit.TestProbe
import akka.actor.ActorRef
import org.specs2.mutable.Before
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import org.specs2.mock.Mockito
import org.overviewproject.models.Document

class RequestDocumentIdsSpec extends Specification with Mockito {

  "RequestDocumentIdsSpec" should {

    "request a document id" in new RequestingScope {
      requestDocumentIds.execute

      idSupplier.expectMsg(DocumentIdRequest(1))
    }

    "wait with WriteDocuments as next step" in new RequestingScope {
      val result = Await.result(requestDocumentIds.execute, Duration.Inf)

      result must beLike {
        case WaitForResponse(nextStep) => ok
      }
    }
  }

  trait RequestingScope extends ActorSystemContext with Before {
    val next = smartMock[TaskStep]
    var idSupplier: TestProbe = _

    var requestDocumentIds: RequestDocumentIds = _

    override def before = {
      idSupplier = TestProbe()
      requestDocumentIds = new TestRequestDocumentIds(idSupplier.ref, next)
    }
  }

  class TestRequestDocumentIds(override val documentIdSupplier: ActorRef, next: TaskStep) extends RequestDocumentIds {
    override protected val documentData = Seq(smartMock[PdfFileDocumentData])
    override protected val documentSetId: Long = 1l
    override protected val nextStep = { d : Seq[Document] => next }
  }
}