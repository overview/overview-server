package controllers

import org.specs2.specification.Scope
import org.specs2.matcher.JsonMatchers
import play.api.libs.json.Json
import scala.concurrent.Future

import controllers.backend.{ApiTokenBackend,StoreBackend,ViewBackend}
import org.overviewproject.tree.orm.{DocumentSetCreationJob,DocumentSetCreationJobState,Tree}
import org.overviewproject.tree.DocumentSetCreationJobType
import org.overviewproject.models.{ApiToken,View}
import org.overviewproject.test.factories.PodoFactory

class ViewControllerSpec extends ControllerSpecification with JsonMatchers {
  trait BaseScope extends Scope {
    val factory = PodoFactory

    val mockStorage = mock[ViewController.Storage]
    val mockAppUrlChecker = mock[ViewController.AppUrlChecker]
    val mockApiTokenBackend = mock[ApiTokenBackend]
    val mockStoreBackend = mock[StoreBackend]
    val mockViewBackend = mock[ViewBackend]

    val controller = new ViewController {
      override protected val storage = mockStorage
      override protected val appUrlChecker = mockAppUrlChecker
      override protected val apiTokenBackend = mockApiTokenBackend
      override protected val storeBackend = mockStoreBackend
      override protected val viewBackend = mockViewBackend
    }

    def fakeViewJob(id: Long) = DocumentSetCreationJob(
      id=id,
      documentSetId=1L,
      treeTitle=Some(s"clustering job ${id}"),
      jobType=DocumentSetCreationJobType.Recluster,
      state=DocumentSetCreationJobState.InProgress
    )

    def fakeViewErrorJob(id: Long) = DocumentSetCreationJob(
      id=id,
      documentSetId=1L,
      treeTitle=Some(s"failed job ${id}"),
      jobType=DocumentSetCreationJobType.Recluster,
      state=DocumentSetCreationJobState.Error
    )

    def fakeTree(id: Long, jobId: Long) = Tree(
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

  "ViewController" should {
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

      "create a View" in new CreateScope {
        override val formBody = validFormBody
        mockAppUrlChecker.check(any[String]) returns Future.successful(Unit)
        mockApiTokenBackend.create(any[Long], any[ApiToken.CreateAttributes]) returns Future.successful(factory.apiToken(token="api-token"))
        mockViewBackend.create(any[Long], any[View.CreateAttributes]) returns Future.failed(new Throwable("goto end"))
        h.status(result)
        there was one(mockViewBackend).create(documentSetId, View.CreateAttributes(
          url="http://localhost:9001",
          apiToken="api-token",
          title="title"
        ))
      }

      "return the View" in new CreateScope {
        override val formBody = validFormBody
        mockAppUrlChecker.check(any[String]) returns Future.successful(())
        mockApiTokenBackend.create(any[Long], any[ApiToken.CreateAttributes]) returns Future.successful(factory.apiToken())
        mockViewBackend.create(any[Long], any[View.CreateAttributes]) returns Future.successful(factory.view(
          id=123L,
          url="http://localhost:9001",
          apiToken="api-token",
          title="title",
          createdAt=new java.sql.Timestamp(1234L)
        ))
        h.status(result) must beEqualTo(h.CREATED)
        val json = h.contentAsString(result)
        json must /("id" -> 123L)
        json must /("url" -> "http://localhost:9001")
        json must /("apiToken" -> "api-token")
        json must /("title" -> "title")
        json must /("createdAt" -> "1970-01-01T00:00:01.234Z")
      }
    }

    "#indexJson" should {
      trait IndexJsonScope extends BaseScope {
        val documentSetId = 1L
        def request = fakeAuthorizedRequest
        def result = controller.indexJson(documentSetId)(request)
        lazy val trees : Iterable[Tree] = Seq()
        lazy val jobs : Iterable[DocumentSetCreationJob] = Seq()
        mockViewBackend.index(documentSetId) returns Future.successful(Seq[View]())
        mockStorage.findTrees(documentSetId) returns trees
        mockStorage.findViewJobs(documentSetId) returns jobs
      }

      "return 200 OK" in new IndexJsonScope {
        h.status(result) must beEqualTo(h.OK)
      }

      "show nothing by default" in new IndexJsonScope {
        h.contentAsString(result) must beEqualTo("[]")
      }

      "show a tree" in new IndexJsonScope {
        override lazy val trees = Seq(fakeTree(1L, 3L))
        val json = h.contentAsString(result)

        json must /#(0) /("type" -> "tree")
        json must /#(0) /("id" -> 1.0)
        json must /#(0) /("jobId" -> 3.0)
        json must /#(0) /("title" -> "title1")
        json must /#(0) /("createdAt" -> "\\d\\d\\d\\d-\\d\\d-\\d\\dT\\d\\d:\\d\\d:\\d\\dZ".r)
        json must /#(0) /("creationData") /#(3) /("lang")
        json must /#(0) /("creationData") /#(3) /("en")
        json must /#(0) /("nDocuments" -> 10)
      }

      "show a view" in new IndexJsonScope {
        val view = factory.view()
        mockViewBackend.index(documentSetId) returns Future.successful(Seq(view))

        val json = h.contentAsString(result)
        json must /#(0) /("type" -> "view")
        json must /#(0) /("id" -> view.id)
        json must /#(0) /("title" -> view.title)
        json must /#(0) /("url" -> view.url)
      }

      "show a job" in new IndexJsonScope {
        override lazy val jobs = Seq(fakeViewJob(1L))
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
        override lazy val jobs = Seq(fakeViewErrorJob(1L))
        val json = h.contentAsString(result)

        json must /#(0) /("type" -> "error")
        json must /#(0) /("id" -> 1.0)
        json must /#(0) /("title" -> "failed job 1")
        json must /#(0) /("progress") /("fraction" -> 0.0)
        json must /#(0) /("creationData") /#(0) /("lang")
        json must /#(0) /("creationData") /#(0) /("en")
      }
    }

    "#destroy" should {
      trait DestroyScope extends BaseScope {
        val documentSetId = 1L
        val viewId = 2L
        val apiToken = "some-token"
        lazy val request = fakeAuthorizedRequest
        def result = controller.destroy(documentSetId, viewId)(request)
        mockViewBackend.show(any) returns Future.successful(Some(factory.view(id=viewId, apiToken=apiToken)))
        mockViewBackend.destroy(any) returns Future.successful(())
        mockStoreBackend.destroy(any) returns Future.successful(())
        mockApiTokenBackend.destroy(any) returns Future.successful(())
      }

      "return NoContent" in new DestroyScope {
        h.status(result) must beEqualTo(h.NO_CONTENT)
      }

      "destroy the View" in new DestroyScope {
        h.status(result)
        there was one(mockViewBackend).destroy(viewId)
      }

      "destroy the Store" in new DestroyScope {
        h.status(result)
        there was one(mockStoreBackend).destroy(apiToken)
      }

      "destroy the ApiToken" in new DestroyScope {
        h.status(result)
        there was one(mockApiTokenBackend).destroy(apiToken)
      }
    }
  }
}
