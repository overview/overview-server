package controllers

import akka.stream.scaladsl.{Source,Sink}
import akka.util.ByteString
import java.util.UUID
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.libs.json.Json
import play.api.libs.streams.Accumulator
import play.api.mvc.{EssentialAction,Results}
import play.api.test.FakeRequest
import scala.concurrent.Future

import com.overviewdocs.messages.DocumentSetCommands
import com.overviewdocs.metadata.{MetadataField,MetadataFieldDisplay,MetadataFieldType,MetadataSchema}
import com.overviewdocs.models.{DocumentSet,FileGroup,GroupedFileUpload}
import controllers.auth.{AuthorizedRequest}
import controllers.backend.{DocumentSetBackend,FileGroupBackend,GroupedFileUploadBackend}
import controllers.util.{JobQueueSender,MassUploadControllerMethods}
import models.{ Session, User }
import test.helpers.MockMessagesApi

class MassUploadControllerSpec extends ControllerSpecification {
  trait BaseScope extends Scope {
    val mockDocumentSetBackend = smartMock[DocumentSetBackend]
    val mockFileGroupBackend = smartMock[FileGroupBackend]
    val mockUploadBackend = smartMock[GroupedFileUploadBackend]
    val mockJobQueueSender = smartMock[JobQueueSender]
    val mockUploadSinkFactory = mock[MassUploadControllerMethods.UploadSinkFactory]

    val controller = new MassUploadController(
      mockDocumentSetBackend,
      mockFileGroupBackend,
      mockJobQueueSender,
      mockUploadBackend,
      mockUploadSinkFactory,
      fakeControllerComponents,
      app.materializer
    )

    val factory = com.overviewdocs.test.factories.PodoFactory
  }

  "#create" should {
    trait CreateScope extends BaseScope {
      val user = User(id=123L, email="user@example.org")
      val session = Session(123L, "127.0.0.1")
      val baseRequest = FakeRequest().withHeaders("Content-Length" -> "20")
      lazy val request = new AuthorizedRequest(baseRequest, new MockMessagesApi, session, user)
      val source = Source.empty[ByteString]
      lazy val action: EssentialAction = controller.create(UUID.randomUUID)
      lazy val result = action(request).run(source)
    }

    "return Ok" in new CreateScope {
      val fileGroup = factory.fileGroup()
      val groupedFileUpload = factory.groupedFileUpload(size=20L, uploadedSize=10L)
      mockFileGroupBackend.findOrCreate(any) returns Future.successful(fileGroup)
      mockUploadBackend.findOrCreate(any) returns Future.successful(groupedFileUpload)
      mockUploadSinkFactory.build(any, any) returns Sink.fold(())((_, _) => ())

      h.status(result) must beEqualTo(h.CREATED)
    }.pendingUntilFixed("AuthorizedBodyParser doesn't allow stubbing")
  }

  "#show" should {
    trait ShowScope extends BaseScope {
      val guid = UUID.randomUUID()
      val userId = 123L
      val userEmail = "show-user@example.org"
      val fileGroupId = 234L

      val user = User(id=userId, email=userEmail)
      def fileGroup: Option[FileGroup] = Some(factory.fileGroup(id=fileGroupId))
      def groupedFileUpload: Option[GroupedFileUpload] = Some(factory.groupedFileUpload())

      mockFileGroupBackend.find(any, any) returns Future(fileGroup)
      mockUploadBackend.find(any, any) returns Future(groupedFileUpload)

      val request = fakeAuthorizedRequest(user)
      lazy val result = controller.show(guid)(request)
    }

    "find the upload" in new ShowScope {
      h.status(result)
      there was one(mockFileGroupBackend).find(userEmail, None)
      there was one(mockUploadBackend).find(fileGroupId, guid)
    }

    "return 404 if the GroupedFileUpload does not exist" in new ShowScope {
      override def groupedFileUpload = None
      h.status(result) must beEqualTo(h.NOT_FOUND)
    }

    "return 404 if the FileGroup does not exist" in new ShowScope {
      override def fileGroup = None
      h.status(result) must beEqualTo(h.NOT_FOUND)
    }

    "return 200 with Content-Length if upload is complete" in new ShowScope {
      override def groupedFileUpload = Some(factory.groupedFileUpload(size=1234, uploadedSize=1234))
      h.status(result) must beEqualTo(h.OK)
      h.header(h.CONTENT_LENGTH, result) must beSome("1234")
    }

    "return PartialContent with Content-Range if upload is not complete" in new ShowScope {
      override def groupedFileUpload = Some(factory.groupedFileUpload(size=2345, uploadedSize=1234))
      h.status(result) must beEqualTo(h.PARTIAL_CONTENT)
      h.header(h.CONTENT_RANGE, result) must beSome("bytes 0-1233/2345")
    }

    "return NoContent if uploaded file is empty" in new ShowScope {
      override def groupedFileUpload = Some(factory.groupedFileUpload(name="filename.abc", size=0, uploadedSize=0))
      h.status(result) must beEqualTo(h.NO_CONTENT)
      h.header(h.CONTENT_DISPOSITION, result) must beSome("attachment; filename=\"filename.abc\"")
    }

    "return NoContent if uploaded file was created but no bytes were ever added" in new ShowScope {
      override def groupedFileUpload = Some(factory.groupedFileUpload(name="filename.abc", size=1234, uploadedSize=0))
      h.status(result) must beEqualTo(h.NO_CONTENT)
      h.header(h.CONTENT_DISPOSITION, result) must beSome("attachment; filename=\"filename.abc\"")
    }
  }

  "#startClustering" should {
    trait StartClusteringScope extends BaseScope {
      def formData = Seq(
        "name" -> "DocumentSet name",
        "lang" -> "sv",
        "split_documents" -> "false",
        "metadata_json" -> """{"foo":"bar"}"""
      )
      val documentSet = factory.documentSet(id=1L)
      val fileGroup = factory.fileGroup(id=2L)
      val modifiedFileGroup = fileGroup.copy(addToDocumentSetId=Some(documentSet.id))
      val user = User(id=123L, email="start-user@example.org")

      mockFileGroupBackend.find(any, any) returns Future.successful(Some(fileGroup))
      mockFileGroupBackend.addToDocumentSet(any, any, any, any, any, any) returns Future.successful(Some(modifiedFileGroup))
      mockDocumentSetBackend.create(any, any) returns Future.successful(documentSet)

      lazy val request = new AuthorizedRequest(
        FakeRequest().withFormUrlEncodedBody(formData: _*),
        new MockMessagesApi,
        Session(user.id, "127.0.0.1"),
        user
      )
      lazy val result = controller.startClustering()(request)
    }

    "redirect" in new StartClusteringScope {
      h.status(result) must beEqualTo(h.SEE_OTHER)
    }

    "create a DocumentSet" in new StartClusteringScope {
      h.status(result)
      there was one(mockDocumentSetBackend).create(
        beLike[DocumentSet.CreateAttributes] { case attributes =>
          attributes.title must beEqualTo("DocumentSet name")
          attributes.metadataSchema must beEqualTo(
            MetadataSchema(1, Seq(MetadataField("foo", MetadataFieldType.String, MetadataFieldDisplay.TextInput)))
          )
        },
        beLike[String] { case s => s must beEqualTo(user.email) }
      )
    }

    "call addToDocumentSet" in new StartClusteringScope {
      h.status(result)
      there was one(mockFileGroupBackend).addToDocumentSet(
        fileGroup.id,
        documentSet.id,
        "sv",
        false,
        true,
        Json.obj("foo" -> "bar")
      )
    }

    "send a message via the JobQueueSender" in new StartClusteringScope {
      h.status(result)
      there was one(mockJobQueueSender).send(DocumentSetCommands.AddDocumentsFromFileGroup(modifiedFileGroup))
    }

    "return NotFound if user has no FileGroup in progress" in new StartClusteringScope {
      mockFileGroupBackend.find(user.email, None) returns Future.successful(None)
      h.status(result) must beEqualTo(h.NOT_FOUND)
      there was no(mockDocumentSetBackend).create(any, any)
      there was no(mockFileGroupBackend).addToDocumentSet(any, any, any, any, any, any)
      there was no(mockJobQueueSender).send(any)
    }
  }

  "#startClusteringExistingDocumentSet" should {
    // XXX See how much of a hack this is? That's because we're combining two
    // actions in one, all the way down the stack (add files + cluster).
    //
    // TODO make adding files and clustering two different things, so we can do
    // everything with half the tests.
    trait StartClusteringExistingDocumentSetScope extends BaseScope {
      def formData = Seq(
        "name" -> "DocumentSet name",
        "lang" -> "sv",
        "split_documents" -> "false",
        "metadata_json" -> """{"foo":"bar"}"""
      )
      val documentSet = factory.documentSet(id=1L)
      val fileGroup = factory.fileGroup(id=2L)
      val modifiedFileGroup = fileGroup.copy(addToDocumentSetId=Some(documentSet.id))
      val user = User(id=123L, email="start-user@example.org")

      mockFileGroupBackend.find(any, any) returns Future.successful(Some(fileGroup))
      mockFileGroupBackend.addToDocumentSet(any, any, any, any, any, any) returns Future.successful(Some(modifiedFileGroup))

      lazy val request = new AuthorizedRequest(
        FakeRequest().withFormUrlEncodedBody(formData: _*),
        new MockMessagesApi,
        Session(user.id, "127.0.0.1"),
        user
      )
      lazy val result = controller.startClusteringExistingDocumentSet(documentSet.id)(request)
    }

    "redirect" in new StartClusteringExistingDocumentSetScope {
      h.status(result) must beEqualTo(h.SEE_OTHER)
    }

    "call addToDocumentSet" in new StartClusteringExistingDocumentSetScope {
      h.status(result)
      there was one(mockFileGroupBackend).addToDocumentSet(
        fileGroup.id,
        documentSet.id,
        "sv",
        false,
        true,
        Json.obj("foo" -> "bar")
      )
    }

    "send a message via the JobQueueSender" in new StartClusteringExistingDocumentSetScope {
      h.status(result)
      there was one(mockJobQueueSender).send(DocumentSetCommands.AddDocumentsFromFileGroup(modifiedFileGroup))
    }

    "return NotFound if user has no FileGroup in progress" in new StartClusteringExistingDocumentSetScope {
      mockFileGroupBackend.find(user.email, None) returns Future.successful(None)
      h.status(result) must beEqualTo(h.NOT_FOUND)
      there was no(mockDocumentSetBackend).create(any, any)
      there was no(mockFileGroupBackend).addToDocumentSet(any, any, any, any, any, any)
      there was no(mockJobQueueSender).send(any)
    }
  }

  "#cancel" should {
    trait CancelScope extends BaseScope {
      mockFileGroupBackend.destroy(any) returns Future.unit
      val user = User(id=123L, email="cancel-user@example.org")

      val request = new AuthorizedRequest(FakeRequest(), new MockMessagesApi, Session(user.id, "127.0.0.1"), user)
      lazy val result = controller.cancel()(request)
    }

    "mark a file group as deleted" in new CancelScope {
      mockFileGroupBackend.find(user.email, None) returns Future.successful(Some(factory.fileGroup(id=234L)))
      h.status(result)
      there was one(mockFileGroupBackend).destroy(234L)
    }

    "do nothing when the file group does not exist" in new CancelScope {
      mockFileGroupBackend.find(user.email, None) returns Future.successful(None)
      h.status(result)
      there was no(mockFileGroupBackend).destroy(any)
    }

    "return Accepted" in new CancelScope {
      mockFileGroupBackend.find(user.email, None) returns Future.successful(None)
      h.status(result) must beEqualTo(h.ACCEPTED)
    }
  }
}
