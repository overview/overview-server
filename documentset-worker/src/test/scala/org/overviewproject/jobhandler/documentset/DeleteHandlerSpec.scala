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
import org.specs2.mutable.Specification
import org.specs2.time.NoTimeConversions


class DeleteHandlerSpec extends Specification with Mockito with NoTimeConversions {

  "DeleteHandler" should {

    trait MockComponents {
      val searchIndex = mock[SearchIndexComponent]

      val deleteResultPromise = Promise[DeleteByQueryResponse]
      val deleteAliasResultPromise = Promise[IndicesAliasesResponse]

      searchIndex.deleteDocuments(anyLong) returns deleteResultPromise.future
      searchIndex.deleteDocumentSetAlias(any) returns deleteAliasResultPromise.future
    }

    "delete documents and alias with the specified documentSetId from the index" in new ActorSystemContext {
      val documentSetId = 2l
      val parentProbe = TestProbe()

      val deleteHandler: TestActorRef[DeleteHandler with MockComponents] = TestActorRef(Props(new DeleteHandler with MockComponents), parentProbe.ref, "DeleteHandler")

      deleteHandler ! DeleteDocumentSet(documentSetId)

      val searchIndex = deleteHandler.underlyingActor.searchIndex

      there was one(searchIndex).deleteDocumentSetAlias(documentSetId)

      val deleteAlias = deleteHandler.underlyingActor.deleteAliasResultPromise
      val aliasResult = mock[IndicesAliasesResponse]
      deleteAlias.success(aliasResult)

      there was one(searchIndex).deleteDocuments(documentSetId)
    }

    "notify parent when deletion of documents and alias completes successfully" in new ActorSystemContext {
      val documentSetId = 2l
      val parentProbe = TestProbe()

      val deleteHandler: TestActorRef[DeleteHandler with MockComponents] = TestActorRef(Props(new DeleteHandler with MockComponents), parentProbe.ref, "DeleteHandler")

      deleteHandler ! DeleteDocumentSet(documentSetId)

      val deleteDocuments = deleteHandler.underlyingActor.deleteResultPromise
      val deleteAlias = deleteHandler.underlyingActor.deleteAliasResultPromise
      val documentResult = mock[DeleteByQueryResponse]
      val aliasResult = mock[IndicesAliasesResponse]

      deleteDocuments.success(documentResult)
      parentProbe.expectNoMsg(10 millis)

      deleteAlias.success(aliasResult)

      parentProbe.expectMsg(JobDone(documentSetId))
    }

    "notify parent when deletion fails" in new ActorSystemContext {
      val documentSetId = 2l
      val parentProbe = TestProbe()

      val deleteHandler: TestActorRef[DeleteHandler with MockComponents] = TestActorRef(Props(new DeleteHandler with MockComponents), parentProbe.ref, "DeleteHandler")

      deleteHandler ! DeleteDocumentSet(documentSetId)

      val deleteDocuments = deleteHandler.underlyingActor.deleteResultPromise
      val deleteAlias = deleteHandler.underlyingActor.deleteAliasResultPromise

      val error = new Exception
      val aliasResult = mock[IndicesAliasesResponse]

      deleteDocuments.failure(error)
      deleteAlias.success(aliasResult)

      // FIXME: We can't distinguish between failure and success right now
      parentProbe.expectMsg(JobDone(documentSetId))

    }
  }
}

