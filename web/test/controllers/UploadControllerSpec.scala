package controllers

import akka.stream.scaladsl.Sink
import akka.util.ByteString
import java.sql.Timestamp
import java.util.UUID
import org.mockito.Matchers
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.libs.streams.Accumulator
import play.api.mvc.{AnyContent,Request,RequestHeader,Result}
import play.api.test.{FakeHeaders,FakeRequest}
import scala.concurrent.Future

import controllers.auth.AuthorizedRequest
import controllers.backend.DocumentSetBackend
import models.upload.{OverviewUpload,OverviewUploadedFile}
import models.{Session,User}
import com.overviewdocs.models.{DocumentSet,Upload,UploadedFile}
import com.overviewdocs.test.factories.{PodoFactory=>factory}

class UploadControllerSpec extends ControllerSpecification with Mockito {
  trait BaseScope extends Scope {
    val guid = UUID.randomUUID
    val documentSetBackend = smartMock[DocumentSetBackend]
    val components = smartMock[UploadController.Components]
    val controller = new UploadController(documentSetBackend, components, fakeControllerComponents)

    def dummyUpload(nBytesUploaded: Long, nBytesTotal: Long): OverviewUpload = OverviewUpload(
      factory.upload(totalSize=nBytesTotal),
      factory.uploadedFile(size=nBytesUploaded, contentDisposition="attachment; filename=foo.csv")
    )
  }

  "#create" should {
    trait CreateScope extends BaseScope {
      val upload: OverviewUpload
      lazy val request = FakeRequest[OverviewUpload]("POST", "/upload", FakeHeaders(), upload, "controllers.UploadController.create")
      lazy val result = controller.create(guid)(request)

      components.fileUploadAccumulator(any, any, any) returns Accumulator(Sink.ignore).map(_ => Right(smartMock[OverviewUpload]))
      components.createCsvImport(any, any, any, any) returns Future.unit
    }

    "return OK if upload is complete" in new CreateScope {
      override val upload = dummyUpload(1L, 1L)
      components.findUpload(any, any) returns Some(upload)
      h.status(result) must beEqualTo(h.OK)
    }

    "return BAD_REQUEST if upload is not complete" in new CreateScope {
      override val upload = dummyUpload(1L, 2L)
      components.findUpload(any, any) returns Some(upload)
      h.status(result) must beEqualTo(h.BAD_REQUEST)
    }
  }

  "#startClustering" should {
    trait StartClusteringScope extends BaseScope {
      val formBody: Seq[(String,String)] = Seq("lang" -> "en")
      lazy val request = fakeAuthorizedRequest.withFormUrlEncodedBody(formBody: _*)
      lazy val result = controller.startClustering(guid)(request)

      components.fileUploadAccumulator(any, any, any) returns Accumulator(Sink.ignore).map(_ => Right(smartMock[OverviewUpload]))
      components.createCsvImport(any, any, any, any) returns Future.unit
      documentSetBackend.create(any, any) returns Future.successful(factory.documentSet())
    }

    "create a CsvImport and delete the upload" in new StartClusteringScope {
      components.findUpload(any, any) returns Some(dummyUpload(1L, 1L))
      h.status(result) must beEqualTo(h.SEE_OTHER)
      there was one(documentSetBackend).create(
        beLike[DocumentSet.CreateAttributes] { case attributes =>
          attributes.title must beEqualTo("foo.csv")
        },
        beLike[String] { case s => s must beEqualTo(request.user.email) }
      )
      there was one(components).createCsvImport(any, any, any, Matchers.eq("en"))
    }

    "not create a CsvImport if upload is not complete" in new StartClusteringScope {
      components.findUpload(any, any) returns Some(dummyUpload(1L, 2L))
      h.status(result) must beEqualTo(h.CONFLICT)
      there was no(documentSetBackend).create(any, any)
      there was no(components).createCsvImport(any, any, any, any)
    }

    "not create a CsvImport if the form is invalid" in new StartClusteringScope {
      components.findUpload(any, any) returns Some(dummyUpload(1L, 1L))
      override val formBody = Seq()
      h.status(result) must beEqualTo(h.BAD_REQUEST)
      there was no(documentSetBackend).create(any, any)
    }

    "not create anything if the upload does not exist" in new StartClusteringScope {
      components.findUpload(any, any) returns None
      h.status(result) must beEqualTo(h.NOT_FOUND)
      there was no(documentSetBackend).create(any, any)
    }
  }

  "#show" should {
    trait ShowScope extends BaseScope {
      val request = fakeAuthorizedRequest
      lazy val result = controller.show(guid)(request)
    }

    "return NOT_FOUND when Upload does not exist" in new ShowScope {
      components.findUpload(any, any) returns None
      h.status(result) must beEqualTo(h.NOT_FOUND)
    }

    "return OK when Upload is complete" in new ShowScope {
      components.findUpload(any, any) returns Some(dummyUpload(1000L, 1000L))
      h.status(result) must beEqualTo(h.OK)
      h.header(h.CONTENT_LENGTH, result) must beSome("1000")
      h.header(h.CONTENT_DISPOSITION, result) must beSome("attachment; filename=foo.csv")
    }

    "return PARTIAL_CONTENT when Upload is incomplete" in new ShowScope {
      components.findUpload(any, any) returns Some(dummyUpload(100L, 1000L))
      h.status(result) must beEqualTo(h.PARTIAL_CONTENT)
      h.header(h.CONTENT_RANGE, result) must beSome("bytes 0-99/1000")
      h.header(h.CONTENT_DISPOSITION, result) must beSome("attachment; filename=foo.csv")
    }
  }
}
