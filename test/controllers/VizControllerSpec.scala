package controllers

import org.specs2.specification.Scope
import org.specs2.matcher.JsonMatchers

import org.overviewproject.tree.orm.{DocumentSetCreationJob,DocumentSetCreationJobState,Tree}
import org.overviewproject.tree.DocumentSetCreationJobType
import org.overviewproject.models.Viz

class VizControllerSpec extends ControllerSpecification with JsonMatchers {
  trait BaseScope extends Scope {

    val mockStorage = mock[VizController.Storage]

    val controller = new VizController {
      override protected val storage = mockStorage
    }

    def fakeVizJob(id: Long) = DocumentSetCreationJob(
      id=id,
      documentSetId=1L,
      treeTitle=Some(s"clustering job ${id}"),
      jobType=DocumentSetCreationJobType.Recluster,
      state=DocumentSetCreationJobState.InProgress
    )

    def fakeVizErrorJob(id: Long) = DocumentSetCreationJob(
      id=id,
      documentSetId=1L,
      treeTitle=Some(s"failed job ${id}"),
      jobType=DocumentSetCreationJobType.Recluster,
      state=DocumentSetCreationJobState.Error
    )

    def fakeViz(id: Long) : Viz = Tree(
      id=id,
      documentSetId=1L,
      jobId=0L,
      title=s"title${id}",
      documentCount=10,
      lang="en",
      description=s"description${id}",
      suppliedStopWords=s"suppliedStopWords${id}",
      importantWords=s"importantWords${id}"
    )
  }

  "VizController" should {
    "indexJson" should {
      trait IndexJsonScope extends BaseScope {
        val documentSetId = 1L
        def request = fakeAuthorizedRequest
        def result = controller.indexJson(documentSetId)(request)
        lazy val vizs : Iterable[Viz] = Seq()
        lazy val jobs : Iterable[DocumentSetCreationJob] = Seq()
        mockStorage.findVizs(documentSetId) returns vizs
        mockStorage.findVizJobs(documentSetId) returns jobs
      }

      "return 200 OK" in new IndexJsonScope {
        h.status(result) must beEqualTo(h.OK)
      }

      "show nothing by default" in new IndexJsonScope {
        h.contentAsString(result) must beEqualTo("[]")
      }

      "show a viz" in new IndexJsonScope {
        lazy val viz = fakeViz(1L)
        override lazy val vizs = Seq(viz)
        val json = h.contentAsString(result)

        json must /#(0) /("type" -> "viz")
        json must /#(0) /("id" -> 1.0)
        json must /#(0) /("title" -> "title1")
        json must /#(0) /("createdAt" -> "\\d\\d\\d\\d-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\dZ".r)
        json must /#(0) /("creationData") /#(0) /("lang")
        json must /#(0) /("creationData") /#(0) /("en")
      }

      "show a job" in new IndexJsonScope {
        lazy val job = fakeVizJob(1L)
        override lazy val jobs = Seq(job)
        val json = h.contentAsString(result)

        json must /#(0) /("type" -> "job")
        json must /#(0) /("id" -> 1.0)
        json must /#(0) /("title" -> "clustering job 1")
        json must /#(0) /("progress") /("fraction" -> 0.0)
        json must /#(0) /("progress") /("state" -> "IN_PROGRESS")
        json must /#(0) /("progress") /("description" -> "")
        json must /#(0) /("creationData") /#(0) /("lang")
        json must /#(0) /("creationData") /#(0) /("en")
      }

      "show an error job" in new IndexJsonScope {
        lazy val job = fakeVizErrorJob(1L)
        override lazy val jobs = Seq(job)
        val json = h.contentAsString(result)

        json must /#(0) /("type" -> "error")
        json must /#(0) /("id" -> 1.0)
        json must /#(0) /("title" -> "failed job 1")
        json must /#(0) /("progress") /("fraction" -> 0.0)
        json must /#(0) /("creationData") /#(0) /("lang")
        json must /#(0) /("creationData") /#(0) /("en")
      }
    }
  }
}
