package controllers

import play.api.Play.{ start, stop }
import org.specs2.mutable.Specification
import play.api.test.FakeApplication
import org.specs2.mock.Mockito
import org.overviewproject.tree.orm.GroupedFileUpload
import java.util.UUID
import play.api.libs.iteratee.Iteratee
import play.api.mvc.Result
import models.OverviewUser
import models.orm.User
import controllers.auth.AuthorizedRequest
import play.api.test.FakeRequest
import play.api.test.Helpers._
import play.api.mvc.RequestHeader
import org.overviewproject.tree.orm.FileGroup
import org.specs2.specification.Scope
import org.specs2.mutable.Before
import play.api.test.FakeHeaders
import org.specs2.execute.PendingUntilFixed
import org.overviewproject.tree.orm.DocumentSet

class MassUploadControllerSpec extends Specification with Mockito {

  step(start(FakeApplication()))

  class TestMassUploadController extends MassUploadController {
    // We can leave this undefined, since it will not be called.
    override def massUploadFileIteratee(userEmail: String, request: RequestHeader, guid: UUID): Iteratee[Array[Byte], Either[Result, GroupedFileUpload]] =
      ???

    override val storage = smartMock[Storage]
    override val messageQueue = smartMock[MessageQueue]
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

    def executeRequest: Result

    lazy val result: Result = executeRequest
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
    val uploadSize = 0
    
    override def createUpload = {
      val upload = smartMock[GroupedFileUpload]
      
      upload.size returns 0
      upload.uploadedSize returns uploadSize
      upload.name returns filename
      
      Some(upload)
    }
  }

  "MassUploadController.create" should {

    trait CreateRequest extends UploadContext {
      override def executeRequest: Result = {
        val baseRequest = FakeRequest[GroupedFileUpload]("POST", s"/files/$guid", FakeHeaders(), foundUpload.get)
        val request = new AuthorizedRequest(baseRequest, user)

        controller.create(guid)(request)
      }
    }

    "send a ProcessFile message when upload is complete" in new CreateRequest with CompleteUpload with InProgressFileGroup {
      status(result) must be equalTo (OK)
      there was one(controller.messageQueue).sendProcessFile(fileGroupId, uploadId)
    }
  }

  "MassUploadController.show" should {

    trait ShowRequest extends UploadContext {
      override def executeRequest: Result = {
        val request = new AuthorizedRequest(FakeRequest(), user)

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
      header(CONTENT_RANGE, result) must beSome(s"0-${uploadSize - 1}/$size")
    }
    
    "return a valid content_range if uploaded file is empty" in new ShowRequest with EmptyUpload with InProgressFileGroup {
      status(result) must be equalTo (PARTIAL_CONTENT)
      header(CONTENT_RANGE, result) must beSome(s"0-0/0")
    }

  }

  "MassUploadController.startClustering" should {

    trait StartClusteringRequest extends UploadContext {
      val fileGroupName = "This becomes the Document Set Name"
      val lang = "sv"
      val stopWords = "ignore these words"
      val importantWords = "important words?"
      val formData = Seq(
        ("name" -> fileGroupName),
        ("lang" -> lang),
        ("supplied_stop_words" -> stopWords),
        ("important_words") -> importantWords)
     val documentSetId = 11l

      override def executeRequest: Result = {
        val request = new AuthorizedRequest(FakeRequest().withFormUrlEncodedBody(formData: _*), user)
        controller.startClustering(request)
      }
      
      override def before: Unit = {
        super.before
        val documentSet = mock[DocumentSet]
        documentSet.id returns documentSetId
        
        controller.storage.createDocumentSet(user.email, fileGroupName, lang, stopWords) returns documentSet
      }
    }
    
    "create job and send startClustering command if user has a FileGroup InProgress" in new StartClusteringRequest with NoUpload with InProgressFileGroup {
      status(result) must be equalTo(SEE_OTHER)
      there was one(controller.storage).createDocumentSet(user.email, fileGroupName, lang, stopWords)
      there was one(controller.storage).createMassUploadDocumentSetCreationJob(
          documentSetId, fileGroupId, lang, stopWords, importantWords)
      there was one(controller.messageQueue).startClustering(fileGroupId, fileGroupName, lang, stopWords)
    }
    
    "return NotFound if user has no FileGroup InProgress" in new StartClusteringRequest with NoUpload with NoFileGroup {
      status(result) must be equalTo(NOT_FOUND)
    }
  }
  step(stop)
}