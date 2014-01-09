package org.overviewproject.jobhandler.documentset

import scala.concurrent.Promise
import scala.concurrent.duration._
import akka.actor._
import akka.testkit.{ TestActorRef, TestProbe }
import org.elasticsearch.action.admin.indices.alias.IndicesAliasesResponse
import org.elasticsearch.action.deletebyquery.DeleteByQueryResponse
import org.overviewproject.jobhandler.documentset.DeleteHandlerProtocol.DeleteDocumentSet
import org.overviewproject.jobhandler.JobProtocol._
import org.overviewproject.test.ActorSystemContext
import org.specs2.mock.Mockito
import org.specs2.mutable.{ Before, Specification }
import org.specs2.time.NoTimeConversions

class DeleteHandlerSpec extends Specification with Mockito with NoTimeConversions {

  "DeleteHandler" should {

    trait MockComponents {
      val searchIndex = mock[SearchIndexComponent]
      val documentSetDeleter = smartMock[DocumentSetDeleter]
      val jobStatusChecker = smartMock[JobStatusChecker]
      
      val deleteResultPromise = Promise[DeleteByQueryResponse]
      val deleteAliasResultPromise = Promise[IndicesAliasesResponse]

      searchIndex.deleteDocuments(anyLong) returns deleteResultPromise.future
      searchIndex.deleteDocumentSetAlias(any) returns deleteAliasResultPromise.future
    }

    abstract class DeleteContext extends ActorSystemContext with Before {
      // need lazy val or def to avoid compiler crash
      protected lazy val aliasResult: IndicesAliasesResponse = smartMock[IndicesAliasesResponse]
      protected lazy val documentResult = smartMock[DeleteByQueryResponse]
      
      protected val documentSetId = 2l

      protected var parentProbe: TestProbe = _
      protected var deleteHandler: TestActorRef[DeleteHandler with MockComponents] = _
      
      protected def searchIndex = deleteHandler.underlyingActor.searchIndex
      protected def deleteAliasResult = deleteHandler.underlyingActor.deleteAliasResultPromise
      protected def deleteDocumentsResult = deleteHandler.underlyingActor.deleteResultPromise
      protected def documentSetDeleter = deleteHandler.underlyingActor.documentSetDeleter
      
      protected def setJobStatus: Unit = 
        deleteHandler.underlyingActor.jobStatusChecker isJobRunning(documentSetId) returns false
        
      override def before = {
        parentProbe = TestProbe()
        deleteHandler = TestActorRef(Props(new DeleteHandler with MockComponents), parentProbe.ref, "DeleteHandler")
        setJobStatus
      }
    }

    abstract class DeleteWhileJobIsRunning extends DeleteContext {
    	override protected def setJobStatus: Unit =
    	  deleteHandler.underlyingActor.jobStatusChecker isJobRunning(documentSetId) returns true thenReturns false
    }
    
    "delete documents and alias with the specified documentSetId from the index" in new DeleteContext {
      deleteHandler ! DeleteDocumentSet(documentSetId)
      deleteAliasResult.success(aliasResult)

      there was one(searchIndex).deleteDocumentSetAlias(documentSetId)
      there was one(searchIndex).deleteDocuments(documentSetId)
    }

    "notify parent when deletion of documents and alias completes successfully" in new DeleteContext {
      deleteHandler ! DeleteDocumentSet(documentSetId)
      deleteDocumentsResult.success(documentResult)

      parentProbe.expectNoMsg(10 millis)

      deleteAliasResult.success(aliasResult)
      parentProbe.expectMsg(JobDone(documentSetId))
    }

    "notify parent when deletion fails" in new DeleteContext {
      val error = new Exception

      deleteHandler ! DeleteDocumentSet(documentSetId)
      deleteDocumentsResult.failure(error)
      deleteAliasResult.success(aliasResult)

      // FIXME: We can't distinguish between failure and success right now
      parentProbe.expectMsg(JobDone(documentSetId))
    }

    "delete document set related data" in new DeleteContext {
      deleteHandler ! DeleteDocumentSet(documentSetId)

      there was one(documentSetDeleter).deleteClientGeneratedInformation(documentSetId)
    }
    
    "wait until clustering job is no longer running before deleting" in new DeleteWhileJobIsRunning {
      deleteHandler ! DeleteDocumentSet(documentSetId)
      
      deleteDocumentsResult.success(documentResult)
      deleteAliasResult.success(aliasResult)
      
      parentProbe.expectNoMsg(50 millis)
      // deleteHandler retries here
      parentProbe.expectMsg(JobDone(documentSetId))
    }
  }
}

