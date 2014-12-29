package controllers

import java.util.UUID
import org.specs2.mock.Mockito
import org.specs2.mutable.Before
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.iteratee.Iteratee
import play.api.mvc.{ RequestHeader, Result }
import play.api.Play.{ start, stop }
import play.api.test.{ FakeApplication, FakeHeaders, FakeRequest }
import play.api.test.Helpers._
import scala.concurrent.Future

import controllers.auth.AuthorizedRequest
import models.OverviewUser
import models.{ Session, User }
import org.overviewproject.tree.orm.{DocumentSet,DocumentSetCreationJob,FileGroup,GroupedFileUpload}
import org.overviewproject.tree.DocumentSetCreationJobType._
import org.overviewproject.tree.orm.DocumentSetCreationJobState._


class MassUploadControllerSpec extends Specification with Mockito {

  step(start(FakeApplication()))

  class TestMassUploadController extends MassUploadController {
    // We can leave this undefined, since it will not be called.
    override def massUploadFileIteratee(userEmail: String, request: RequestHeader, guid: UUID): Iteratee[Array[Byte], Either[Result, GroupedFileUpload]] =
      ???

    override val storage = smartMock[Storage]
    override val messageQueue = smartMock[MessageQueue]
    messageQueue.startClustering(any, any) returns Future.successful(Unit)
  }

  trait FileGroupProvider {
    def createFileGroup: Option[FileGroup]
  }

  trait UploadProvider {
    def createUpload: Option[GroupedFileUpload]
  }

  trait UploadContext extends Before with FileGroupProvider with UploadProvider {
    val fileGroupId = 1l
    val guid = UUID.randomUUID
    val user = OverviewUser(User(1l))
    val controller = new TestMassUploadController
    var foundFileGroup: Option[FileGroup] = _
    var foundUpload: Option[GroupedFileUpload] = _

    def before: Unit = {
      foundFileGroup = createFileGroup
      foundUpload = createUpload

      controller.storage.findCurrentFileGroup(user.email) returns foundFileGroup
      foundFileGroup map { fg => fg.id returns fileGroupId }

      controller.storage.findGroupedFileUpload(fileGroupId, guid) returns foundUpload
      foundUpload map { u => u.fileGroupId returns fileGroupId }
    }

    def executeRequest: Future[Result]

    lazy val result = executeRequest
  }

  trait NoFileGroup extends FileGroupProvider {
    override def createFileGroup() = None
  }

  trait InProgressFileGroup extends FileGroupProvider {
    override def createFileGroup = Some(smartMock[FileGroup])
  }

  trait NoUpload extends UploadProvider {
    override def createUpload = None
  }

  trait UploadInfo {
    val size = 1000l
    val filename = "foo.pdf"
    val contentDisposition = s"attachment ; filename=$filename"
  }

  trait CompleteUpload extends UploadProvider with UploadInfo {
    val uploadId = 10l

    override def createUpload = {
      val upload = smartMock[GroupedFileUpload]

      upload.id returns uploadId
      upload.size returns size
      upload.uploadedSize returns size
      upload.name returns filename

      Some(upload)
    }
  }

  trait IncompleteUpload extends UploadProvider with UploadInfo {
    val uploadSize = 500l

    override def createUpload = {
      val upload = smartMock[GroupedFileUpload]

      upload.size returns size
      upload.uploadedSize returns uploadSize
      upload.name returns filename

      Some(upload)
    }
  }

  trait EmptyUpload extends UploadProvider with UploadInfo {
    val uploadSize = 0L

    override def createUpload = {
      val upload = smartMock[GroupedFileUpload]

      upload.size returns 0
      upload.uploadedSize returns uploadSize
      upload.name returns filename
      upload.contentDisposition returns contentDisposition

      Some(upload)
    }
  }

  "MassUploadController.create" should {

    trait CreateRequest extends UploadContext {
      override def executeRequest = {
        val baseRequest = FakeRequest[GroupedFileUpload]("POST", s"/files/$guid", FakeHeaders(), foundUpload.get)
        val request = new AuthorizedRequest(baseRequest, Session(user.id, "127.0.0.1"), user.toUser)

        controller.create(guid)(request)
      }
    }

    "return Ok when upload is complete" in new CreateRequest with CompleteUpload with InProgressFileGroup {
      status(result) must be equalTo (OK)
    }

    "return BadRequest if upload is not complete" in new CreateRequest with IncompleteUpload with InProgressFileGroup {
      status(result) must be equalTo (BAD_REQUEST)
    }

    "return Ok when the upload is an empty file" in new CreateRequest with EmptyUpload with InProgressFileGroup {
      override val size = 0L
      override val uploadSize = 0L
      status(result) must be equalTo (OK)
    }
  }

  "MassUploadController.show" should {

    trait ShowRequest extends UploadContext {
      override def executeRequest = {
        val request = new AuthorizedRequest(FakeRequest(), Session(user.id, "127.0.0.1"), user.toUser)

        controller.show(guid)(request)
      }
    }

    "return NOT_FOUND if upload does not exist" in new ShowRequest with NoUpload with InProgressFileGroup {
      status(result) must be equalTo (NOT_FOUND)
    }

    "return NOT_FOUND if no InProgress FileGroup exists" in new ShowRequest with CompleteUpload with NoFileGroup {
      status(result) must be equalTo (NOT_FOUND)
    }

    "return Ok with content length if upload is complete" in new ShowRequest with CompleteUpload with InProgressFileGroup {
      status(result) must be equalTo (OK)
      header(CONTENT_LENGTH, result) must beSome(s"$size")
    }

    "return PartialContent with content range if upload is not complete" in new ShowRequest with IncompleteUpload with InProgressFileGroup {
      status(result) must be equalTo (PARTIAL_CONTENT)
      header(CONTENT_RANGE, result) must beSome(s"bytes 0-${uploadSize - 1}/$size")
    }

    "return NoContent uploaded file is empty" in new ShowRequest with EmptyUpload with InProgressFileGroup {
      status(result) must be equalTo (NO_CONTENT)
      header(CONTENT_DISPOSITION, result) must beSome(contentDisposition)
    }

  }

  "MassUploadController.startClustering" should {

    trait StartClusteringRequest extends UploadContext {
      val fileGroupName = "This becomes the Document Set Name"
      val lang = "sv"
      val splitDocuments = false
      val splitDocumentsString = s"$splitDocuments"
      val stopWords = "ignore these words"
      val importantWords = "important words?"
      def formData = Seq(
        ("name" -> fileGroupName),
        ("lang" -> lang),
        ("split_documents" -> splitDocumentsString),
        ("supplied_stop_words" -> stopWords),
        ("important_words") -> importantWords)
      val documentSetId = 11l
      val job = mock[DocumentSetCreationJob]
      
      override def executeRequest = {
        val request = new AuthorizedRequest(FakeRequest().withFormUrlEncodedBody(formData: _*), Session(user.id, "127.0.0.1"), user.toUser)
        controller.startClustering(request)
      }

      override def before: Unit = {
        super.before
        val documentSet = mock[DocumentSet]
        documentSet.id returns documentSetId

        controller.storage.createDocumentSet(user.email, fileGroupName, lang) returns documentSet
        controller.storage.createMassUploadDocumentSetCreationJob(
            documentSetId, fileGroupId, lang, splitDocuments, stopWords, importantWords) returns job
      }
    }

    "create job and send ClusterFileGroup command if user has a FileGroup InProgress" in new StartClusteringRequest with NoUpload with InProgressFileGroup {
      status(result) must be equalTo (SEE_OTHER)
      there was one(controller.storage).createDocumentSet(user.email, fileGroupName, lang)
      there was one(controller.storage).createMassUploadDocumentSetCreationJob(
        documentSetId, fileGroupId, lang, splitDocumentsString != "false", stopWords, importantWords)
      there was one(controller.messageQueue).startClustering(job, fileGroupName)
    }

    "set splitDocuments=true when asked" in new StartClusteringRequest with NoUpload with InProgressFileGroup {
      override val splitDocumentsString = "true"
      result
      there was one(controller.storage).createMassUploadDocumentSetCreationJob(
        documentSetId, fileGroupId, lang, true, stopWords, importantWords)
    }

    "return NotFound if user has no FileGroup InProgress" in new StartClusteringRequest with NoUpload with NoFileGroup {
      status(result) must be equalTo (NOT_FOUND)
    }
  }

  "MassUploadController.cancelUpload" should {

    trait CancelClusteringRequest extends UploadContext {

      override def executeRequest = {
        val request = new AuthorizedRequest(FakeRequest(), Session(user.id, "127.0.0.1"), user.toUser)

        controller.cancelUpload(request)
      }
    }

    "send cancel message" in new CancelClusteringRequest with IncompleteUpload with InProgressFileGroup {
      result

      there was one(controller.storage).deleteFileGroupByUser(user.toUser.email)
    }
  }
  step(stop)
}
