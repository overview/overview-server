/*
 * DocumentSetControllerSpec.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, June 2012
 */
package controllers

import org.specs2.specification.Scope

import org.overviewproject.tree.Ownership
import org.overviewproject.tree.orm.{DocumentSet, DocumentSetCreationJob, DocumentSetCreationJobState, Tree}
import org.overviewproject.tree.DocumentSetCreationJobType
import controllers.auth.AuthorizedRequest
import controllers.forms.DocumentSetForm.Credentials
import org.overviewproject.tree.orm.DocumentSetUser
import org.overviewproject.tree.orm.finders.ResultPage
import models.OverviewUser


class DocumentSetControllerSpec extends ControllerSpecification {
  trait BaseScope extends Scope {
    val IndexPageSize = 10
    val mockStorage = mock[DocumentSetController.Storage]
    val controller = new DocumentSetController {
      override val indexPageSize = IndexPageSize
      override val storage = mockStorage
    }

    def fakeJob(documentSetId: Long, id: Long) = DocumentSetCreationJob(
      id=id,
      documentSetId=documentSetId,
      jobType=DocumentSetCreationJobType.FileUpload,
      state=DocumentSetCreationJobState.InProgress
    )
    def fakeDocumentSet(id: Long) = DocumentSet(
      id=id
    )
    def fakeTree(documentSetId: Long, id: Long) = Tree(
      id=id,
      documentSetId=documentSetId,
      title="title",
      documentCount=10,
      lang="en"
    )
  }

  "DocumentSetController" should {
    "update" should {
      trait UpdateScope extends BaseScope {
        val documentSetId = 1
        def formBody : Seq[(String,String)] = Seq("public" -> "false", "title" -> "foo")
        def request = fakeAuthorizedRequest.withFormUrlEncodedBody(formBody: _*)
        def result = controller.update(documentSetId)(request)
      }

      "return 200 ok" in new UpdateScope {
        mockStorage.findDocumentSet(documentSetId) returns Some(DocumentSet(id=documentSetId))
        h.status(result) must beEqualTo(h.OK)
      }

      "update the DocumentSet" in new UpdateScope {
        mockStorage.findDocumentSet(documentSetId) returns Some(DocumentSet(id=documentSetId))
        h.status(result) // invoke it
        there was one(mockStorage).insertOrUpdateDocumentSet(any)
      }

      "return NotFound if document set is bad" in new UpdateScope {
        mockStorage.findDocumentSet(anyLong) returns None
        h.status(result) must beEqualTo(h.NOT_FOUND)
      }

      "return BadRequest if form input is bad" in new UpdateScope {
        mockStorage.findDocumentSet(documentSetId) returns Some(DocumentSet(id=documentSetId))
        override def formBody = Seq("public" -> "maybe", "title" -> "bar")
        h.status(result) must beEqualTo(h.BAD_REQUEST)
      }
    }

    "showJson" should {
      trait ShowJsonScope extends BaseScope {
        val documentSetId = 1
        def result = controller.showJson(documentSetId)(fakeAuthorizedRequest)
      }

      "return NotFound if document set is not present" in new ShowJsonScope {
        mockStorage.findDocumentSet(documentSetId) returns None
        h.status(result) must beEqualTo(h.NOT_FOUND)
      }

      "return Ok if the document set exists but no trees do" in new ShowJsonScope {
        mockStorage.findDocumentSet(documentSetId) returns Some(fakeDocumentSet(documentSetId))
        mockStorage.findTreesByDocumentSet(documentSetId) returns Seq()
        h.status(result) must beEqualTo(h.OK)
      }

      "return Ok if the document set exists with trees" in new ShowJsonScope {
        mockStorage.findDocumentSet(documentSetId) returns Some(fakeDocumentSet(documentSetId))
        val fakeTrees : Seq[Tree] = Seq(fakeTree(1L, 2L), fakeTree(1L, 3L))
        mockStorage.findTreesByDocumentSet(documentSetId) returns fakeTrees
        h.status(result) must beEqualTo(h.OK)
      }
    }

    "index" should {
      trait IndexScope extends BaseScope {
        def pageNumber = 1

        def fakeDocumentSets : Seq[DocumentSet] = Seq(fakeDocumentSet(1L))
        mockStorage.findDocumentSets(anyString, anyInt, anyInt) answers { (_) => ResultPage(fakeDocumentSets, IndexPageSize, pageNumber) }
        def fakeTrees : Seq[Tree] = Seq(fakeTree(1L, 2L), fakeTree(1L, 3L))
        mockStorage.findTreesByDocumentSets(any[Seq[Long]]) answers { (_) => fakeTrees }
        def fakeJobs : Seq[(DocumentSetCreationJob, DocumentSet, Long)] = Seq()
        mockStorage.findDocumentSetCreationJobs(anyString) answers { (_) => fakeJobs }

        lazy val result = controller.index(pageNumber)(fakeAuthorizedRequest)
        lazy val j = jodd.lagarto.dom.jerry.Jerry.jerry(h.contentAsString(result))
      }

      "return Ok" in new IndexScope {
        h.status(result) must beEqualTo(h.OK)
      }

      "show page 1 if the page number is too low" in new IndexScope {
        override def pageNumber = 0
        h.status(result) must beEqualTo(h.OK) // load page
        there was one(mockStorage).findDocumentSets(anyString, anyInt, org.mockito.Matchers.eq[java.lang.Integer](1))
      }

      "show multiple pages" in new IndexScope {
        override def fakeDocumentSets = (1 until IndexPageSize * 2).map(fakeDocumentSet(_))
        h.contentAsString(result) must contain("/documentsets?page=2")
      }

      "show multiple document sets per page" in new IndexScope {
        override def fakeDocumentSets = (1 until IndexPageSize).map(fakeDocumentSet(_))
        h.contentAsString(result) must not contain("/documentsets?page=2")
      }

      "bind trees to their document sets" in new IndexScope {
        override def fakeDocumentSets = Seq(fakeDocumentSet(1L), fakeDocumentSet(2L))
        override def fakeTrees = Seq(fakeTree(1L, 10L), fakeTree(2L, 20L), fakeTree(1L, 30L))

        val ds1 = j.$("[data-document-set-id='1']")
        val ds2 = j.$("[data-document-set-id='2']")

        ds1.find("[data-tree-id='10']").length must beEqualTo(1)
        ds2.find("[data-tree-id='20']").length must beEqualTo(1)
        ds1.find("[data-tree-id='30']").length must beEqualTo(1)
      }

      "show jobs" in new IndexScope {
        override def fakeJobs = Seq((fakeJob(2, 2), fakeDocumentSet(2), 0), (fakeJob(3, 3), fakeDocumentSet(3), 1))

        j.$("[data-document-set-creation-job-id='2']").length must beEqualTo(1)
        j.$("[data-document-set-creation-job-id='3']").length must beEqualTo(1)
      }
    }
  }
}
