package controllers

import java.sql.Timestamp
import java.util.UUID
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.libs.iteratee.{Done,Input,Iteratee}
import play.api.mvc.{AnyContent,Request,RequestHeader,Result}
import play.api.test.{FakeHeaders,FakeRequest,FakeApplication}
import scala.concurrent.Future

import controllers.auth.AuthorizedRequest
import controllers.backend.DocumentSetBackend
import models.upload.{OverviewUpload,OverviewUploadedFile}
import models.{Session,User}
import com.overviewdocs.models.{DocumentSet,Upload,UploadedFile}
import com.overviewdocs.test.factories.{PodoFactory=>factory}

class UploadControllerSpec extends ControllerSpecification with Mockito {
  class AController(upload: Option[OverviewUpload] = None) extends UploadController with TestController {
    override val documentSetBackend = smartMock[DocumentSetBackend]
    documentSetBackend.create(any, any) returns Future.successful(factory.documentSet(id=456L))

    var jobStarted: Boolean = false
    var lang: Option[String] = None
    var stopWords: Option[String] = None
    var importantWords: Option[String] = None

    override def fileUploadIteratee(userId: Long, guid: UUID, requestHeader: RequestHeader): Iteratee[Array[Byte], Either[Result, OverviewUpload]] =
      Done(Right(mock[OverviewUpload]), Input.EOF)

    override def findUpload(userId: Long, guid: UUID): Option[OverviewUpload] = upload

    override def createCsvImport(
      documentSet: DocumentSet,
      upload: Upload,
      uploadedFile: UploadedFile,
      aLang: String
    ) = {
      jobStarted = true
      lang = Some(aLang)
      Future.successful(())
    }
  }

  trait BaseScope extends Scope {
    val guid = UUID.randomUUID
  }

  def dummyUpload(nBytesUploaded: Long, nBytesTotal: Long): OverviewUpload = OverviewUpload(
    factory.upload(totalSize=nBytesTotal),
    factory.uploadedFile(size=nBytesUploaded, contentDisposition="attachment; filename=foo.csv")
  )

  "#create" should {
    trait CreateScope extends BaseScope {
      val controller = new AController()
      val upload: OverviewUpload
      lazy val request = FakeRequest[OverviewUpload]("POST", "/upload", FakeHeaders(), upload, "controllers.UploadController.create")
      lazy val result = controller.create(guid)(request)
    }

    "return OK if upload is complete" in new CreateScope {
      override val upload = dummyUpload(1L, 1L)
      h.status(result) must beEqualTo(h.OK)
    }

    "return BAD_REQUEST if upload is not complete" in new CreateScope {
      override val upload = dummyUpload(1L, 2L)
      h.status(result) must beEqualTo(h.BAD_REQUEST)
    }
  }

  "#startClustering" should {
    trait StartClusteringScope extends BaseScope {
      val maybeUpload: Option[OverviewUpload]
      val formBody: Seq[(String,String)] = Seq("lang" -> "en", "supplied_stop_words" -> "some stop words")
      lazy val controller = new AController(maybeUpload)
      lazy val request = fakeAuthorizedRequest.withFormUrlEncodedBody(formBody: _*)
      lazy val result = controller.startClustering(guid)(request)
    }

    "create a CsvImport and delete the upload" in new StartClusteringScope {
      override val maybeUpload = Some(dummyUpload(1L, 1L))
      h.status(result) must beEqualTo(h.SEE_OTHER)
      there was one(controller.documentSetBackend).create(
        beLike[DocumentSet.CreateAttributes] { case attributes =>
          attributes.title must beEqualTo("foo.csv")
        },
        beLike[String] { case s => s must beEqualTo(request.user.email) }
      )
      controller.jobStarted must beTrue
      controller.lang must beSome("en")
    }

    "not create a CsvImport if upload is not complete" in new StartClusteringScope {
      override val maybeUpload = Some(dummyUpload(1L, 2L))
      h.status(result) must beEqualTo(h.CONFLICT)
      there was no(controller.documentSetBackend).create(any, any)
      controller.jobStarted must beFalse
    }

    "not create a CsvImport if the form is invalid" in new StartClusteringScope {
      override val maybeUpload = Some(dummyUpload(1L, 1L))
      override val formBody = Seq()
      h.status(result) must beEqualTo(h.BAD_REQUEST)
      there was no(controller.documentSetBackend).create(any, any)
    }

    "not create anything if the upload does not exist" in new StartClusteringScope {
      override val maybeUpload = None
      h.status(result) must beEqualTo(h.NOT_FOUND)
      there was no(controller.documentSetBackend).create(any, any)
    }
  }

  "#show" should {
    trait ShowScope extends BaseScope {
      val maybeUpload: Option[OverviewUpload]
      val request = fakeAuthorizedRequest
      lazy val controller = new AController(maybeUpload)
      lazy val result = controller.show(guid)(request)
    }

    "return NOT_FOUND when Upload does not exist" in new ShowScope {
      override val maybeUpload = None
      h.status(result) must beEqualTo(h.NOT_FOUND)
    }

    "return OK when Upload is complete" in new ShowScope {
      override val maybeUpload = Some(dummyUpload(1000L, 1000L))
      h.status(result) must beEqualTo(h.OK)
      h.header(h.CONTENT_LENGTH, result) must beSome("1000")
      h.header(h.CONTENT_DISPOSITION, result) must beSome("attachment; filename=foo.csv")
    }

    "return PARTIAL_CONTENT when Upload is incomplete" in new ShowScope {
      override val maybeUpload = Some(dummyUpload(100L, 1000L))
      h.status(result) must beEqualTo(h.PARTIAL_CONTENT)
      h.header(h.CONTENT_RANGE, result) must beSome("bytes 0-99/1000")
      h.header(h.CONTENT_DISPOSITION, result) must beSome("attachment; filename=foo.csv")
    }
  }
}
