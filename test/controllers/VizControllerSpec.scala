package controllers

import org.specs2.specification.Scope
import org.specs2.matcher.JsonMatchers
import play.api.libs.json.Json
import scala.concurrent.Future

import controllers.backend.{ApiTokenBackend,VizBackend}
import org.overviewproject.tree.orm.{DocumentSetCreationJob,DocumentSetCreationJobState,Tree}
import org.overviewproject.tree.DocumentSetCreationJobType
import org.overviewproject.models.{ApiToken,Viz,VizLike}
import org.overviewproject.test.factories.PodoFactory

class VizControllerSpec extends ControllerSpecification with JsonMatchers {
  trait BaseScope extends Scope {
    val factory = PodoFactory

    val mockStorage = mock[VizController.Storage]
    val mockAppUrlChecker = mock[VizController.AppUrlChecker]
    val mockApiTokenBackend = mock[ApiTokenBackend]
    val mockVizBackend = mock[VizBackend]

    val controller = new VizController {
      override protected val storage = mockStorage
      override protected val apiTokenBackend = mockApiTokenBackend
      override protected val appUrlChecker = mockAppUrlChecker
      override protected val vizBackend = mockVizBackend
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

    def fakeViz(id: Long, jobId: Long) : VizLike = Tree( // TODO don't rely on Tree
      id=id,
      documentSetId=1L,
      rootNodeId=3L,
      jobId=jobId,
      title=s"title${id}",
      documentCount=10,
      lang="en",
      description=s"description${id}",
      suppliedStopWords=s"suppliedStopWords${id}",
      importantWords=s"importantWords${id}"
    )
  }

  "VizController" should {
    "#create" should {
      trait CreateScope extends BaseScope {
        val documentSetId = 1L
        val formBody: Seq[(String,String)] = Seq()
        def request = fakeAuthorizedRequest.withFormUrlEncodedBody(formBody: _*)
        lazy val result = controller.create(documentSetId)(request)

        val validFormBody = Seq("title" -> "title", "url" -> "http://localhost:9001")
      }

      "return 400 Bad Request on invalid form body" in new CreateScope {
        override val formBody = Seq("title" -> "", "url" -> "http://localhost:9001")
        h.status(result) must beEqualTo(h.BAD_REQUEST)
        there was no(mockAppUrlChecker).check(any[String])
      }

      "check the URL" in new CreateScope {
        override val formBody = validFormBody
        mockAppUrlChecker.check(any[String]) returns Future.failed(new Throwable("some error"))
        h.status(result)
        there was one(mockAppUrlChecker).check("http://localhost:9001/metadata")
      }

      "return 400 Bad Request on invalid URL" in new CreateScope {
        override val formBody = validFormBody
        mockAppUrlChecker.check(any[String]) returns Future.failed(new Throwable("some error"))
        h.status(result) must beEqualTo(h.BAD_REQUEST)
        there was no(mockApiTokenBackend).create(any[Long], any[ApiToken.CreateAttributes])
      }

      "create an ApiToken" in new CreateScope {
        override val formBody = validFormBody
        mockAppUrlChecker.check(any[String]) returns Future.successful(Unit)
        mockApiTokenBackend.create(any[Long], any[ApiToken.CreateAttributes]) returns Future.failed(new Throwable("goto end"))
        h.status(result)
        there was one(mockApiTokenBackend).create(documentSetId, ApiToken.CreateAttributes(
          email="user@example.org",
          description="title"
        ))
      }

      "create a Viz" in new CreateScope {
        override val formBody = validFormBody
        mockAppUrlChecker.check(any[String]) returns Future.successful(Unit)
        mockApiTokenBackend.create(any[Long], any[ApiToken.CreateAttributes]) returns Future.successful(factory.apiToken(token="api-token"))
        mockVizBackend.create(any[Long], any[Viz.CreateAttributes]) returns Future.failed(new Throwable("goto end"))
        h.status(result)
        there was one(mockVizBackend).create(documentSetId, Viz.CreateAttributes(
          url="http://localhost:9001",
          apiToken="api-token",
          title="title",
          json=Json.obj()
        ))
      }

      "return the Viz" in new CreateScope {
        override val formBody = validFormBody
        mockAppUrlChecker.check(any[String]) returns Future.successful(())
        mockApiTokenBackend.create(any[Long], any[ApiToken.CreateAttributes]) returns Future.successful(factory.apiToken())
        mockVizBackend.create(any[Long], any[Viz.CreateAttributes]) returns Future.successful(factory.viz(
          id=123L,
          url="http://localhost:9001",
          apiToken="api-token",
          title="title",
          createdAt=new java.sql.Timestamp(1234L),
          json=Json.obj("foo" -> "bar")
        ))
        h.status(result) must beEqualTo(h.CREATED)
        val json = h.contentAsString(result)
        json must /("id" -> 123L)
        json must /("url" -> "http://localhost:9001")
        json must /("apiToken" -> "api-token")
        json must /("title" -> "title")
        json must /("createdAt" -> "1970-01-01T00:00:01.234Z")
        json must /("json") /("foo" -> "bar")
      }
    }

    "#indexJson" should {
      trait IndexJsonScope extends BaseScope {
        val documentSetId = 1L
        def request = fakeAuthorizedRequest
        def result = controller.indexJson(documentSetId)(request)
        lazy val vizs : Iterable[VizLike] = Seq()
        lazy val jobs : Iterable[DocumentSetCreationJob] = Seq()
        mockVizBackend.index(documentSetId) returns Future.successful(Seq[Viz]())
        mockStorage.findVizs(documentSetId) returns vizs
        mockStorage.findVizJobs(documentSetId) returns jobs
      }

      "return 200 OK" in new IndexJsonScope {
        h.status(result) must beEqualTo(h.OK)
      }

      "show nothing by default" in new IndexJsonScope {
        h.contentAsString(result) must beEqualTo("[]")
      }

      "show a tree" in new IndexJsonScope {
        lazy val viz = fakeViz(1L, 3L)
        override lazy val vizs = Seq(viz)
        val json = h.contentAsString(result)

        json must /#(0) /("type" -> "viz")
        json must /#(0) /("id" -> 1.0)
        json must /#(0) /("jobId" -> 3.0)
        json must /#(0) /("title" -> "title1")
        json must /#(0) /("createdAt" -> "\\d\\d\\d\\d-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\dZ".r)
        json must /#(0) /("creationData") /#(3) /("lang")
        json must /#(0) /("creationData") /#(3) /("en")
        json must /#(0) /("nDocuments" -> 10)
      }

      "show a viz" in new IndexJsonScope {
        val viz = factory.viz()
        mockVizBackend.index(documentSetId) returns Future.successful(Seq(viz))

        val json = h.contentAsString(result)
        json must /#(0) /("id" -> viz.id)
        json must /#(0) /("title" -> viz.title)
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
