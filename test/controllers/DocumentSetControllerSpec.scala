/*
 * DocumentSetControllerSpec.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, June 2012
 */
package controllers

import org.overviewproject.jobs.models.Delete
import org.overviewproject.tree.DocumentSetCreationJobType
import org.overviewproject.tree.orm._
import org.overviewproject.tree.orm.finders.ResultPage
import org.specs2.specification.Scope
import org.overviewproject.tree.orm.DocumentSetCreationJobState
import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import org.overviewproject.tree.DocumentSetCreationJobType
import org.overviewproject.tree.DocumentSetCreationJobType._
import org.overviewproject.jobs.models.DeleteTreeJob
import org.overviewproject.jobs.models.CancelFileUpload

class DocumentSetControllerSpec extends ControllerSpecification {
  trait BaseScope extends Scope {
    val IndexPageSize = 10
    val mockStorage = mock[DocumentSetController.Storage]
    val mockJobQueue = mock[DocumentSetController.JobMessageQueue]

    val controller = new DocumentSetController {
      override val indexPageSize = IndexPageSize
      override val storage = mockStorage
      override val jobQueue = mockJobQueue
    }

    def fakeJob(documentSetId: Long, id: Long, fileGroupId: Long,
                state: DocumentSetCreationJobState = InProgress,
                jobType: DocumentSetCreationJobType = FileUpload) =
      DocumentSetCreationJob(
        id = id,
        documentSetId = documentSetId,
        fileGroupId = Some(fileGroupId),
        jobType = jobType,
        state = state)
    def fakeTreeErrorJob(documentSetId: Long, id: Long) = DocumentSetCreationJob(
      id = id,
      documentSetId = documentSetId,
      treeTitle = Some(s"Failed job ${id}"),
      jobType = DocumentSetCreationJobType.Recluster,
      state = DocumentSetCreationJobState.Error)
    def fakeDocumentSet(id: Long) = DocumentSet(
      id = id)
    def fakeTree(documentSetId: Long, id: Long) = Tree(
      id = id,
      documentSetId = documentSetId,
      title = "title",
      documentCount = 10,
      lang = "en")
  }

  "DocumentSetController" should {
    "update" should {
      trait UpdateScope extends BaseScope {
        val documentSetId = 1
        def formBody: Seq[(String, String)] = Seq("public" -> "false", "title" -> "foo")
        def request = fakeAuthorizedRequest.withFormUrlEncodedBody(formBody: _*)
        def result = controller.update(documentSetId)(request)
      }

      "return 200 ok" in new UpdateScope {
        mockStorage.findDocumentSet(documentSetId) returns Some(DocumentSet(id = documentSetId))
        h.status(result) must beEqualTo(h.OK)
      }

      "update the DocumentSet" in new UpdateScope {
        mockStorage.findDocumentSet(documentSetId) returns Some(DocumentSet(id = documentSetId))
        h.status(result) // invoke it
        there was one(mockStorage).insertOrUpdateDocumentSet(any)
      }

      "return NotFound if document set is bad" in new UpdateScope {
        mockStorage.findDocumentSet(anyLong) returns None
        h.status(result) must beEqualTo(h.NOT_FOUND)
      }

      "return BadRequest if form input is bad" in new UpdateScope {
        mockStorage.findDocumentSet(documentSetId) returns Some(DocumentSet(id = documentSetId))
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
        mockStorage.findTreeErrorJobsByDocumentSet(documentSetId) returns Seq()
        h.status(result) must beEqualTo(h.OK)
      }

      "return Ok if the document set exists with trees" in new ShowJsonScope {
        mockStorage.findDocumentSet(documentSetId) returns Some(fakeDocumentSet(documentSetId))
        val fakeTrees: Seq[Tree] = Seq(fakeTree(1L, 2L), fakeTree(1L, 3L))
        mockStorage.findTreesByDocumentSet(documentSetId) returns fakeTrees
        mockStorage.findTreeErrorJobsByDocumentSet(documentSetId) returns Seq()
        h.status(result) must beEqualTo(h.OK)
      }
    }

    "index" should {
      trait IndexScope extends BaseScope {
        def pageNumber = 1

        def fakeDocumentSets: Seq[DocumentSet] = Seq(fakeDocumentSet(1L))
        mockStorage.findDocumentSets(anyString, anyInt, anyInt) answers { (_) => ResultPage(fakeDocumentSets, IndexPageSize, pageNumber) }
        def fakeTrees: Seq[Tree] = Seq(fakeTree(1L, 2L), fakeTree(1L, 3L))
        mockStorage.findTreesByDocumentSets(any[Seq[Long]]) answers { (_) => fakeTrees }
        def fakeTreeErrorJobs: Seq[DocumentSetCreationJob] = Seq()
        mockStorage.findTreeErrorJobsByDocumentSets(any[Seq[Long]]) answers { (_) => fakeTreeErrorJobs }
        def fakeJobs: Seq[(DocumentSetCreationJob, DocumentSet, Long)] = Seq()
        mockStorage.findDocumentSetCreationJobs(anyString) answers { (_) => fakeJobs }

        def request = fakeAuthorizedRequest

        lazy val result = controller.index(pageNumber)(request)
        lazy val j = jodd.lagarto.dom.jerry.Jerry.jerry(h.contentAsString(result))
      }

      "return Ok" in new IndexScope {
        h.status(result) must beEqualTo(h.OK)
      }

      "return Ok if there are only jobs" in new IndexScope {
        override def fakeDocumentSets = Seq()
        override def fakeJobs = Seq((fakeJob(2, 2, 2), fakeDocumentSet(2), 0))

        h.status(result) must beEqualTo(h.OK)
      }

      "return Ok if there are only document sets" in new IndexScope {
        override def fakeJobs = Seq()

        h.status(result) must beEqualTo(h.OK)
      }

      "redirect to examples if there are no document sets or jobs" in new IndexScope {
        override def fakeDocumentSets = Seq()
        override def fakeJobs = Seq()

        h.status(result) must beEqualTo(h.SEE_OTHER)
      }

      "flash when redirecting" in new IndexScope {
        override def fakeDocumentSets = Seq()
        override def fakeJobs = Seq()

        override def request = super.request.withFlash("foo" -> "bar")
        h.flash(result).data must beEqualTo(Map("foo" -> "bar"))
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
        h.contentAsString(result) must not contain ("/documentsets?page=2")
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

      "bind errored tree jobs to their document sets" in new IndexScope {
        override def fakeDocumentSets = Seq(fakeDocumentSet(1L), fakeDocumentSet(2L))
        override def fakeTrees = Seq(fakeTree(1L, 10L), fakeTree(2L, 20L))
        override def fakeTreeErrorJobs = Seq(fakeTreeErrorJob(1L, 11L), fakeTreeErrorJob(2L, 21L))

        val ds1 = j.$("[data-document-set-id='1']")
        val ds2 = j.$("[data-document-set-id='2']")

        ds1.find("[data-job-id='11']").length must beEqualTo(1)
        ds2.find("[data-job-id='21']").length must beEqualTo(1)
      }

      "show jobs" in new IndexScope {
        override def fakeJobs = Seq((fakeJob(2, 2, 2), fakeDocumentSet(2), 0), (fakeJob(3, 3, 3), fakeDocumentSet(3), 1))

        j.$("[data-document-set-creation-job-id='2']").length must beEqualTo(1)
        j.$("[data-document-set-creation-job-id='3']").length must beEqualTo(1)
      }
    }

    "delete" should {

      trait DeleteScope extends BaseScope {
        val documentSetId = 1l
        val fileGroupId = 10l
        val documentSet = fakeDocumentSet(documentSetId)

        def request = fakeAuthorizedRequest

        lazy val result = controller.delete(documentSetId)(request)

        mockStorage.findDocumentSet(documentSetId) returns Some(documentSet)

        protected def setupJob(jobState: DocumentSetCreationJobState, jobType: DocumentSetCreationJobType = FileUpload): Unit =
          mockStorage.cancelJob(documentSetId) returns Some(fakeJob(documentSetId, 10l, fileGroupId, jobState, jobType))
      }

      "mark document set deleted and send delete request if there is no job running" in new DeleteScope {
        mockStorage.cancelJob(documentSetId) returns None

        h.status(result) must beEqualTo(h.SEE_OTHER)

        there was one(mockStorage).deleteDocumentSet(documentSet)
        there was one(mockJobQueue).send(Delete(documentSetId))
      }

      "mark document set and job deleted and send delete request if job has failed" in new DeleteScope {
        setupJob(Error)

        h.status(result) must beEqualTo(h.SEE_OTHER)

        there was one(mockStorage).cancelJob(documentSet)
        there was one(mockStorage).deleteDocumentSet(documentSet)
        there was one(mockJobQueue).send(Delete(documentSetId))
      }

      "mark document set and job deleted and send delete request if job is cancelled" in new DeleteScope {
        setupJob(Cancelled)

        h.status(result) must beEqualTo(h.SEE_OTHER)

        there was one(mockStorage).cancelJob(documentSet)
        there was one(mockStorage).deleteDocumentSet(documentSet)
        there was one(mockJobQueue).send(Delete(documentSetId))

      }

      "mark job deleted and send delete job request if reclustering job has not started clustering" in new DeleteScope {
        setupJob(NotStarted, Recluster)

        h.status(result) must beEqualTo(h.SEE_OTHER)

        there was one(mockStorage).cancelJob(documentSet)
        there was one(mockJobQueue).send(DeleteTreeJob(documentSetId))
      }

      "mark job deleted if reclustering job has started clustering" in new DeleteScope {
        setupJob(InProgress, Recluster)

        h.status(result) must beEqualTo(h.SEE_OTHER)

        there was one(mockStorage).cancelJob(documentSet)
      }

      "mark document set and job deleted and send delete request if import job has not started clustering" in new DeleteScope {
        setupJob(NotStarted)

        h.status(result) must beEqualTo(h.SEE_OTHER)

        there was one(mockStorage).cancelJob(documentSet)
        there was one(mockStorage).deleteDocumentSet(documentSet)
        there was one(mockJobQueue).send(Delete(documentSetId))
      }

      "mark document set and job deleted and send delete request if import job has started clustering" in new DeleteScope {
        setupJob(InProgress)

        h.status(result) must beEqualTo(h.SEE_OTHER)

        there was one(mockStorage).cancelJob(documentSet)
        there was one(mockStorage).deleteDocumentSet(documentSet)
        there was one(mockJobQueue).send(Delete(documentSetId, waitForJobRemoval = true))
      }

      "mark document set and job deleted and send cancel request if files have been uploaded" in new DeleteScope {
        setupJob(FilesUploaded)

        h.status(result) must beEqualTo(h.SEE_OTHER)

        there was one(mockStorage).cancelJob(documentSet)
        there was one(mockJobQueue).send(CancelFileUpload(documentSetId, fileGroupId))
      }

      "mark document set and job deleted and send cancel request if text extraction in progress" in new DeleteScope {
        setupJob(TextExtractionInProgress)

        h.status(result) must beEqualTo(h.SEE_OTHER)

        there was one(mockStorage).cancelJob(documentSet)
        there was one(mockJobQueue).send(CancelFileUpload(documentSetId, fileGroupId))
      }
    }
  }
}
