package controllers

import java.util.UUID
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.libs.iteratee.Enumerator
import play.api.mvc.BodyParsers.parse
import play.api.test.FakeRequest
import scala.concurrent.Future

import controllers.auth.AuthorizedRequest
import controllers.backend.{FileGroupBackend,GroupedFileUploadBackend}
import models.{ Session, User }
import org.overviewproject.models.{FileGroup,GroupedFileUpload}
import org.overviewproject.tree.orm.{DocumentSet,DocumentSetCreationJob}
import org.overviewproject.tree.DocumentSetCreationJobType._
import org.overviewproject.tree.orm.DocumentSetCreationJobState._

class MassUploadControllerSpec extends ControllerSpecification {
  trait BaseScope extends Scope {
    val mockFileGroupBackend = smartMock[FileGroupBackend]
    val mockUploadBackend = smartMock[GroupedFileUploadBackend]
    val mockStorage = smartMock[MassUploadController.Storage]
    val mockMessageQueue = smartMock[MassUploadController.MessageQueue]

    val controller = new MassUploadController {
      override val fileGroupBackend = mockFileGroupBackend
      override val groupedFileUploadBackend = mockUploadBackend
      override val storage = mockStorage
      override val messageQueue = mockMessageQueue
      override val uploadBodyParserFactory = ((guid: UUID) => parse.empty)
    }

    val factory = org.overviewproject.test.factories.PodoFactory
  }

  "#create" should {
    trait CreateScope extends BaseScope {
      val user = User(id=123L, email="user@example.org")
      val request = new AuthorizedRequest(FakeRequest(), Session(user.id, "127.0.0.1"), user)
      val guid = UUID.randomUUID()
      lazy val result = controller.create(guid)(request)
    }

    "return Ok" in new CreateScope {
      h.status(Enumerator(Array[Byte]()).run(result)) must beEqualTo(h.CREATED)
    }
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

      val request = new AuthorizedRequest(FakeRequest(), Session(userId, "127.0.0.1"), user)
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
      val fileGroupName = "This becomes the Document Set Name"
      val lang = "sv"
      val splitDocuments = false
      val splitDocumentsString = s"$splitDocuments"
      val stopWords = "ignore these words"
      val importantWords = "important words?"
      def formData = Seq(
        "name" -> fileGroupName,
        "lang" -> lang,
        "split_documents" -> splitDocumentsString,
        "supplied_stop_words" -> stopWords,
        "important_words" -> importantWords
      )
      val documentSetId = 11L
      val job = factory.documentSetCreationJob()
      val user = User(id=123L, email="start-user@example.org")
      val fileGroup = factory.fileGroup(id=234L)
      val documentSet = factory.documentSet(id=documentSetId)

      mockFileGroupBackend.find(any, any) returns Future(Some(fileGroup))
      mockFileGroupBackend.update(any, any) returns Future(fileGroup.copy(completed=true))
      mockStorage.createDocumentSet(any, any, any) returns documentSet.toDeprecatedDocumentSet
      mockStorage.createMassUploadDocumentSetCreationJob(any, any, any, any, any, any) returns job.toDeprecatedDocumentSetCreationJob
      mockMessageQueue.startClustering(any, any) returns Future(())

      lazy val request = new AuthorizedRequest(FakeRequest().withFormUrlEncodedBody(formData: _*), Session(user.id, "127.0.0.1"), user)
      lazy val result = controller.startClustering()(request)
    }

    "redirect" in new StartClusteringScope {
      h.status(result) must beEqualTo(h.SEE_OTHER)
    }

    "create a DocumentSetCreationJob" in new StartClusteringScope {
      h.status(result)
      there was one(mockStorage).createDocumentSet(user.email, fileGroupName, lang)
      there was one(mockStorage).createMassUploadDocumentSetCreationJob(
        documentSetId, 234L, lang, false, stopWords, importantWords)
    }

    "send a ClusterFileGroup message" in new StartClusteringScope {
      h.status(result)
      there was one(mockMessageQueue).startClustering(job.toDeprecatedDocumentSetCreationJob, fileGroupName)
    }

    "set splitDocuments=true when asked" in new StartClusteringScope {
      override val splitDocumentsString = "true"
      h.status(result)
      there was one(mockStorage).createMassUploadDocumentSetCreationJob(
        documentSetId, 234L, lang, true, stopWords, importantWords)
    }

    "return NotFound if user has no FileGroup in progress" in new StartClusteringScope {
      mockFileGroupBackend.find(user.email, None) returns Future.successful(None)
      h.status(result) must beEqualTo(h.NOT_FOUND)
      there was no(mockStorage).createDocumentSet(any, any, any)
    }
  }

  "#cancel" should {
    trait CancelScope extends BaseScope {
      mockFileGroupBackend.destroy(any) returns Future.successful(())
      val user = User(id=123L, email="cancel-user@example.org")

      val request = new AuthorizedRequest(FakeRequest(), Session(user.id, "127.0.0.1"), user)
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
