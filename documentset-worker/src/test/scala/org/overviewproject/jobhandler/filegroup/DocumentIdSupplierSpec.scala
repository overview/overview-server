package org.overviewproject.jobhandler.filegroup

import org.specs2.mutable.Specification
import org.overviewproject.test.ActorSystemContext
import org.specs2.mutable.Before
import akka.actor.ActorRef
import org.overviewproject.jobhandler.filegroup.task.step.DocumentIdRequest
import org.overviewproject.jobhandler.filegroup.task.FileGroupTaskWorkerProtocol.RequestResponse
import akka.testkit.TestActorRef
import scala.concurrent.Future


class DocumentIdSupplierSpec extends Specification {

  "DocumentIdSupplier" should {
    
    "return document ids when no documents exist" in new DocumentSetContext {
      documentIdSupplier ! DocumentIdRequest(requestId, numberOfIds)
      
      expectMsg(RequestResponse(requestId, expectedIds))
    }
    
    
    "handle multiple requests" in new DocumentSetContext {
      val firstRequest = numberOfIds - 2
      val secondRequest = numberOfIds - firstRequest
      
      documentIdSupplier ! DocumentIdRequest(requestId, firstRequest)
      expectMsg(RequestResponse(requestId, expectedIds.take(firstRequest)))

      documentIdSupplier ! DocumentIdRequest(requestId, secondRequest)
      expectMsg(RequestResponse(requestId, expectedIds.drop(firstRequest)))
      
    }
    
  }
  
  abstract class DocumentSetContext extends ActorSystemContext with Before {
    val documentSetId: Long = 1l
    val requestId: Int = 23
    val numberOfIds: Int = 5
    val expectedIds = Seq.tabulate(numberOfIds)(n => (documentSetId << 32) | (n + 1))
    
    protected def maxId = documentSetId << 32
    
    var documentIdSupplier: ActorRef = _
    

    override def before = {
      
      documentIdSupplier = TestActorRef(new TestDocumentIdSupplier(documentSetId, maxId))
    }
  }
  
  class TestDocumentIdSupplier(override protected val documentSetId: Long, maxId: Long) extends DocumentIdSupplier {
    
    override protected def findMaxDocumentId = Future.successful(maxId)
  }
}