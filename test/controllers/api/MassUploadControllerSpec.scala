package controllers.api

import java.util.UUID
import org.specs2.specification.Scope
import play.api.libs.iteratee.{Enumerator,Iteratee}
import play.api.libs.json.Json
import play.api.mvc.{EssentialAction,Results}
import play.api.test.FakeRequest
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import controllers.auth.{ApiAuthorizedRequest,ApiTokenFactory}
import controllers.backend.{FileGroupBackend,GroupedFileUploadBackend}
import org.overviewproject.models.{ApiToken,FileGroup,GroupedFileUpload}
import org.overviewproject.tree.orm.{DocumentSet,DocumentSetCreationJob}
import org.overviewproject.tree.DocumentSetCreationJobType._
import org.overviewproject.tree.orm.DocumentSetCreationJobState._

class MassUploadControllerSpec extends ApiControllerSpecification {
  trait BaseScope extends Scope {
    val mockFileGroupBackend = smartMock[FileGroupBackend]
    val mockUploadBackend = smartMock[GroupedFileUploadBackend]
    val mockApiTokenFactory = smartMock[ApiTokenFactory]
    val mockStorage = smartMock[MassUploadController.Storage]
    val mockMessageQueue = smartMock[MassUploadController.MessageQueue]
    val mockUploadIterateeFactory = mock[(GroupedFileUpload,Long) => Iteratee[Array[Byte],Unit]]

    lazy val controller = new MassUploadController {
      override val fileGroupBackend = mockFileGroupBackend
      override val groupedFileUploadBackend = mockUploadBackend
      override val apiTokenFactory = mockApiTokenFactory
      override val storage = mockStorage
      override val messageQueue = mockMessageQueue
      override val uploadIterateeFactory = mockUploadIterateeFactory
    }

    val factory = org.overviewproject.test.factories.PodoFactory
    val apiToken = factory.apiToken(createdBy="user@example.org", token="api-token")
  }

  "#create" should {
    trait CreateScope extends BaseScope {
      val baseRequest = FakeRequest().withHeaders("Content-Length" -> "20")
      lazy val request = new ApiAuthorizedRequest(baseRequest, apiToken)
      val enumerator: Enumerator[Array[Byte]] = Enumerator()
      lazy val action: EssentialAction = controller.create(UUID.randomUUID)
      lazy val result = enumerator.run(action(request))
    }

    "return a Result if ApiTokenFactory returns a Left[Result]" in new CreateScope {
      mockApiTokenFactory.loadAuthorizedApiToken(any, any) returns Future.successful(Left(Results.BadRequest))
      status(result) must beEqualTo(BAD_REQUEST)
    }

    "return Ok" in new CreateScope {
      val fileGroup = factory.fileGroup()
      val groupedFileUpload = factory.groupedFileUpload(size=20L, uploadedSize=10L)
      mockApiTokenFactory.loadAuthorizedApiToken(any, any) returns Future.successful(Right(apiToken))
      mockFileGroupBackend.findOrCreate(any) returns Future.successful(fileGroup)
      mockUploadBackend.findOrCreate(any) returns Future.successful(groupedFileUpload)
      mockUploadIterateeFactory(any, any) returns Iteratee.ignore[Array[Byte]]

      status(result) must beEqualTo(CREATED)
    }
  }

  "#show" should {
    trait ShowScope extends BaseScope {
      val guid = UUID.randomUUID()
      val fileGroupId = 234L

      def fileGroup: Option[FileGroup] = Some(factory.fileGroup(id=fileGroupId))
      def groupedFileUpload: Option[GroupedFileUpload] = Some(factory.groupedFileUpload())

      mockFileGroupBackend.find(any, any) returns Future(fileGroup)
      mockUploadBackend.find(any, any) returns Future(groupedFileUpload)

      val request = new ApiAuthorizedRequest(FakeRequest(), apiToken)
      lazy val result = controller.show(guid)(request)
    }

    "find the upload" in new ShowScope {
      status(result)
      there was one(mockFileGroupBackend).find("user@example.org", Some("api-token"))
      there was one(mockUploadBackend).find(fileGroupId, guid)
    }

    "return 404 if the GroupedFileUpload does not exist" in new ShowScope {
      override def groupedFileUpload = None
      status(result) must beEqualTo(NOT_FOUND)
    }

    "return 404 if the FileGroup does not exist" in new ShowScope {
      override def fileGroup = None
      status(result) must beEqualTo(NOT_FOUND)
    }

    "return 200 with Content-Length if upload is complete" in new ShowScope {
      override def groupedFileUpload = Some(factory.groupedFileUpload(size=1234, uploadedSize=1234))
      status(result) must beEqualTo(OK)
      header(CONTENT_LENGTH, result) must beSome("1234")
    }

    "return PartialContent with Content-Range if upload is not complete" in new ShowScope {
      override def groupedFileUpload = Some(factory.groupedFileUpload(size=2345, uploadedSize=1234))
      status(result) must beEqualTo(PARTIAL_CONTENT)
      header(CONTENT_RANGE, result) must beSome("bytes 0-1233/2345")
    }

    "return NoContent if uploaded file is empty" in new ShowScope {
      override def groupedFileUpload = Some(factory.groupedFileUpload(name="filename.abc", size=0, uploadedSize=0))
      status(result) must beEqualTo(NO_CONTENT)
      header(CONTENT_DISPOSITION, result) must beSome("attachment; filename=\"filename.abc\"")
    }

    "return NoContent if uploaded file was created but no bytes were ever added" in new ShowScope {
      override def groupedFileUpload = Some(factory.groupedFileUpload(name="filename.abc", size=1234, uploadedSize=0))
      status(result) must beEqualTo(NO_CONTENT)
      header(CONTENT_DISPOSITION, result) must beSome("attachment; filename=\"filename.abc\"")
    }
  }

  "#startClustering" should {
    trait StartClusteringScope extends BaseScope {
      val fileGroupName = "This becomes the Document Set Name"
      val lang = "sv"
      val splitDocuments = false
      val stopWords = "ignore these words"
      val importantWords = "important words?"
      def formData = Json.obj(
        "name" -> fileGroupName,
        "lang" -> lang,
        "split_documents" -> splitDocuments,
        "supplied_stop_words" -> stopWords,
        "important_words" -> importantWords
      )
      val documentSetId = 11L
      val job = factory.documentSetCreationJob()
      val fileGroup = factory.fileGroup(id=234L)
      val documentSet = factory.documentSet(id=documentSetId)

      mockFileGroupBackend.find(any, any) returns Future(Some(fileGroup))
      mockFileGroupBackend.update(any, any) returns Future(fileGroup.copy(completed=true))
      mockStorage.createDocumentSet(any, any) returns documentSet.toDeprecatedDocumentSet
      mockStorage.createMassUploadDocumentSetCreationJob(any, any, any, any, any, any) returns job.toDeprecatedDocumentSetCreationJob
      mockMessageQueue.startClustering(any, any) returns Future(())

      lazy val request = new ApiAuthorizedRequest(FakeRequest().withJsonBody(formData), apiToken)
      lazy val result = controller.startClustering()(request)
    }

    "return Created" in new StartClusteringScope {
      status(result) must beEqualTo(CREATED)
    }

    "create a DocumentSetCreationJob" in new StartClusteringScope {
      status(result)
      there was one(mockStorage).createDocumentSet("user@example.org", fileGroupName)
      there was one(mockStorage).createMassUploadDocumentSetCreationJob(
        documentSetId, 234L, lang, false, stopWords, importantWords)
    }

    "send a ClusterFileGroup message" in new StartClusteringScope {
      status(result)
      there was one(mockMessageQueue).startClustering(job.toDeprecatedDocumentSetCreationJob, fileGroupName)
    }

    "set splitDocuments=true when asked" in new StartClusteringScope {
      override val splitDocuments = true
      status(result)
      there was one(mockStorage).createMassUploadDocumentSetCreationJob(
        documentSetId, 234L, lang, true, stopWords, importantWords)
    }

    "return NotFound if user has no FileGroup in progress" in new StartClusteringScope {
      mockFileGroupBackend.find(any, any) returns Future.successful(None)
      status(result) must beEqualTo(NOT_FOUND)
      there was no(mockStorage).createDocumentSet(any, any)
    }
  }

  "#cancel" should {
    trait CancelScope extends BaseScope {
      mockFileGroupBackend.destroy(any) returns Future.successful(())

      val request = new ApiAuthorizedRequest(FakeRequest(), apiToken)
      lazy val result = controller.cancel()(request)
    }

    "mark a file group as deleted" in new CancelScope {
      mockFileGroupBackend.find(any, any) returns Future.successful(Some(factory.fileGroup(id=234L)))
      status(result)
      there was one(mockFileGroupBackend).destroy(234L)
    }

    "do nothing when the file group does not exist" in new CancelScope {
      mockFileGroupBackend.find(any, any) returns Future.successful(None)
      status(result)
      there was no(mockFileGroupBackend).destroy(any)
    }

    "return Accepted" in new CancelScope {
      mockFileGroupBackend.find(any, any) returns Future.successful(None)
      status(result) must beEqualTo(ACCEPTED)
    }
  }
}
