package controllers.api

import akka.stream.scaladsl.{Sink,Source}
import akka.util.ByteString
import java.util.UUID
import org.specs2.specification.Scope
import play.api.libs.json.Json
import play.api.mvc.{EssentialAction,Results}
import play.api.test.FakeRequest
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.overviewdocs.messages.DocumentSetCommands
import com.overviewdocs.models.{FileGroup,GroupedFileUpload}
import controllers.auth.{ApiAuthorizedRequest,ApiTokenFactory}
import controllers.backend.{FileGroupBackend,GroupedFileUploadBackend}
import controllers.util.JobQueueSender

class MassUploadControllerSpec extends ApiControllerSpecification {
  trait BaseScope extends Scope {
    val mockFileGroupBackend = smartMock[FileGroupBackend]
    val mockUploadBackend = smartMock[GroupedFileUploadBackend]
    val mockApiTokenFactory = smartMock[ApiTokenFactory]
    val mockJobQueueSender = smartMock[JobQueueSender]
    val mockUploadSinkFactory = mock[(GroupedFileUpload,Long) => Sink[ByteString, Future[Unit]]]

    lazy val controller = new MassUploadController with TestController {
      override val fileGroupBackend = mockFileGroupBackend
      override val groupedFileUploadBackend = mockUploadBackend
      override val apiTokenFactory = mockApiTokenFactory
      override val jobQueueSender = mockJobQueueSender
      override val uploadSinkFactory = mockUploadSinkFactory
    }

    val factory = com.overviewdocs.test.factories.PodoFactory
    val apiToken = factory.apiToken(createdBy="user@example.org", token="api-token", documentSetId=Some(1L))
  }

  "#index" should {
    trait IndexScope extends BaseScope {
      lazy val request = new ApiAuthorizedRequest(FakeRequest(), apiToken)
      lazy val result = controller.index(request)

      val fileGroup = factory.fileGroup()
      val upload1 = factory.groupedFileUpload(name="foo.txt", contentType="application/foo", size=20L, uploadedSize=10L, guid=UUID.randomUUID)
      val upload2 = factory.groupedFileUpload(name="bar.pdf", contentType="application/bar", size=30L, uploadedSize=30L, guid=UUID.randomUUID)

      mockFileGroupBackend.findOrCreate(any) returns Future.successful(fileGroup)
      mockUploadBackend.index(any) returns Future.successful(Seq(upload1, upload2))
    }

    "return JSON files" in new IndexScope {
      status(result) must beEqualTo(OK)
      val json = contentAsString(result)

      json must */("name" -> "foo.txt")
      json must */("total" -> 20)
      json must */("loaded" -> 10)
      json must */("guid" -> upload1.guid.toString)

      json must */("name" -> "bar.pdf")
      json must */("total" -> 30)
      json must */("loaded" -> 30)
      json must */("guid" -> upload2.guid.toString)
    }
  }

  "#create" should {
    trait CreateScope extends BaseScope {
      val baseRequest = FakeRequest().withHeaders(
        "Content-Length" -> "20", 
        "Content-Disposition" -> "attachment; filename=foo.pdf")
      lazy val request = new ApiAuthorizedRequest(baseRequest, apiToken)
      val source = Source.empty[ByteString]
      lazy val action: EssentialAction = controller.create(UUID.randomUUID)
      lazy val result = action(request).run(source)
    }

    "return 400 if api token authorization fails" in new CreateScope {
      mockApiTokenFactory.loadAuthorizedApiToken(any, any) returns Future.successful(Left(Results.BadRequest))
      status(result) must beEqualTo(BAD_REQUEST)
    }

    "return Ok" in new CreateScope {
      val fileGroup = factory.fileGroup()
      val groupedFileUpload = factory.groupedFileUpload(size=20L, uploadedSize=10L)
      mockApiTokenFactory.loadAuthorizedApiToken(any, any) returns Future.successful(Right(apiToken))
      mockFileGroupBackend.findOrCreate(any) returns Future.successful(fileGroup)
      mockUploadBackend.findOrCreate(any) returns Future.successful(groupedFileUpload)
      mockUploadSinkFactory(any, any) returns Sink.fold(())((_, _) => ())

      status(result) must beEqualTo(CREATED)
    }

    "return 400 for missing content-length" in new CreateScope {
      val badRequest = FakeRequest().withHeaders("Content-Disposition" -> "attachment; filename=foo.pdf")
      override lazy val request = new ApiAuthorizedRequest(badRequest, apiToken)
      mockApiTokenFactory.loadAuthorizedApiToken(any, any) returns Future.successful(Right(apiToken))

      status(result) must beEqualTo(BAD_REQUEST)
    }

    "return 400 for missing content-disposition" in new CreateScope {
      val badRequest = FakeRequest().withHeaders("Content-Length" -> "20")
      override lazy val request = new ApiAuthorizedRequest(badRequest, apiToken)
      mockApiTokenFactory.loadAuthorizedApiToken(any, any) returns Future.successful(Right(apiToken))

      status(result) must beEqualTo(BAD_REQUEST)
    }

    "return 400 for unparseable content-disposition" in new CreateScope {
      val badRequest = FakeRequest().withHeaders("Content-Length" -> "20", "Content-Disposition" -> "fsgfdgdf")
      override lazy val request = new ApiAuthorizedRequest(badRequest, apiToken)
      mockApiTokenFactory.loadAuthorizedApiToken(any, any) returns Future.successful(Right(apiToken))

      status(result) must beEqualTo(BAD_REQUEST)
    }

    "return 400 for empty filename" in new CreateScope {
      val badRequest = FakeRequest().withHeaders("Content-Length" -> "20", "Content-Disposition" -> "attachment; filename=")
      override lazy val request = new ApiAuthorizedRequest(badRequest, apiToken)
      mockApiTokenFactory.loadAuthorizedApiToken(any, any) returns Future.successful(Right(apiToken))

      status(result) must beEqualTo(BAD_REQUEST)
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
      lazy val request = new ApiAuthorizedRequest(FakeRequest().withJsonBody(formData), apiToken)
      lazy val documentSetId = {
        val ret = request.apiToken.documentSetId.get
        factory.documentSet(id=ret)
        ret
      }
      lazy val result = controller.startClustering()(request)

      def formData = Json.obj(
        "name" -> "DocumentSet name",
        "lang" -> "sv",
        "split_documents" -> false,
        "metadata_json" -> """{"foo":"bar"}"""
      )

      mockFileGroupBackend.find(any, any) returns Future.successful(Some(factory.fileGroup(id=234L)))
      mockFileGroupBackend.addToDocumentSet(any, any, any, any, any, any) returns Future.successful(Some(
        factory.fileGroup(id=234L, addToDocumentSetId=Some(123L))
      ))
    }

    "return Created" in new StartClusteringScope {
      status(result) must beEqualTo(CREATED)
    }

    "call addToDocumentSet" in new StartClusteringScope {
      status(result)
      there was one(mockFileGroupBackend).addToDocumentSet(
        234L,
        documentSetId,
        "sv",
        false,
        true,
        Json.obj("foo" -> "bar")
      )
    }

    "send a message to the JobQueue" in new StartClusteringScope {
      status(result)
      there was one(mockJobQueueSender).send(DocumentSetCommands.AddDocumentsFromFileGroup(
        factory.fileGroup(id=234L, addToDocumentSetId=Some(123L))
      ))
    }

    "return NotFound if user has no FileGroup in progress" in new StartClusteringScope {
      mockFileGroupBackend.find(any, any) returns Future.successful(None)
      status(result) must beEqualTo(NOT_FOUND)
      there was no(mockFileGroupBackend).addToDocumentSet(any, any, any, any, any, any)
      there was no(mockJobQueueSender).send(any)
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
