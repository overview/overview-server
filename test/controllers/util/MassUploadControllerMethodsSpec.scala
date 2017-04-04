package controllers.util

import java.util.UUID
import play.api.libs.iteratee.{Enumerator,Iteratee}
import play.api.test.FakeRequest
import org.specs2.matcher.JsonMatchers
import org.specs2.specification.Scope
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future

import controllers.backend.{FileGroupBackend,GroupedFileUploadBackend}
import com.overviewdocs.models.GroupedFileUpload
import com.overviewdocs.test.factories.PodoFactory

class MassUploadControllerMethodsSpec extends controllers.ControllerSpecification with JsonMatchers {
  "Create" should {
    trait CreateScope extends Scope {
      val factory = PodoFactory
      val guid = UUID.randomUUID
      val wantJsonResponse = false
      val mockFileGroupBackend = smartMock[FileGroupBackend]
      val mockUploadBackend = smartMock[GroupedFileUploadBackend]
      val mockUploadIterateeFactory = mock[(GroupedFileUpload,Long) => Iteratee[Array[Byte],Unit]]

      lazy val action = MassUploadControllerMethods.Create(
        "user@example.org",
        Some("api-token"),
        guid,
        mockFileGroupBackend,
        mockUploadBackend,
        mockUploadIterateeFactory,
        wantJsonResponse
      )

      val headers: Seq[(String,String)] = Seq("Content-Length" -> "20", "Content-Disposition" -> "attachment; filename=foobar.txt")
      lazy val request = FakeRequest().withHeaders(headers: _*)
      val enumerator: Enumerator[Array[Byte]] = Enumerator()
      lazy val result = enumerator.run(action(request))

      val fileGroup = factory.fileGroup()
      val groupedFileUpload = factory.groupedFileUpload(size=20L, uploadedSize=10L)

      mockFileGroupBackend.findOrCreate(any) returns Future.successful(fileGroup)
      mockUploadBackend.findOrCreate(any) returns Future.successful(groupedFileUpload)
      mockUploadIterateeFactory(any, any) returns Iteratee.ignore[Array[Byte]]
    }

    "return BadRequest if missing Content-Length and Content-Range" in new CreateScope {
      override val headers = Seq()
      h.status(result) must beEqualTo(h.BAD_REQUEST)
      h.contentAsString(result) must beEqualTo("Request must have Content-Range or Content-Length header")
    }

    "return BadRequest if uploading past uploadedSize" in new CreateScope {
      override val headers = Seq("Content-Range" -> "bytes 12-19/20", "Content-Disposition" -> "attachment; filename=foobar.txt")
      h.status(result) must beEqualTo(h.BAD_REQUEST)
      h.contentAsString(result) must beEqualTo("Tried to resume past last uploaded byte. Resumed at byte 12, but only 10 bytes have been uploaded.")
    }

    "return BadRequest message as json if requested" in new CreateScope {
      override val headers = Seq()
      override val wantJsonResponse = true
      h.status(result) must beEqualTo(h.BAD_REQUEST)
      h.contentAsString(result) must /("code" -> "illegal-arguments")
    }

    "create a GroupedFileUpload using Content-Length, Content-Type and Content-Disposition" in new CreateScope {
      override val headers = Seq(
        "Content-Length" -> "20",
        "Content-Type" -> "application/foo",
        "Content-Disposition" -> "attachment; filename=foobar.txt"
      )
      h.status(result)
      there was one(mockUploadBackend).findOrCreate(GroupedFileUpload.CreateAttributes(
        fileGroup.id,
        guid,
        "application/foo",
        "foobar.txt",
        20L
      ))
    }

    "decode Content-Disposition into a complex filename" in new CreateScope {
      override val headers = Seq(
        "Content-Length" -> "20",
        "Content-Type" -> "application/foo",
        "Content-Disposition" -> "attachment; filename*=UTF-8''%E5%85%83%E6%B0%97%E3%81%AA%E3%81%A7%E3%81%99%E3%81%8B%EF%BC%9F.pdf"
      )
      h.status(result)
      there was one(mockUploadBackend).findOrCreate(GroupedFileUpload.CreateAttributes(
        fileGroup.id,
        guid,
        "application/foo",
        "元気なですか？.pdf",
        20L
      ))
    }

    "create a GroupedUploadIteratee using Content-Length" in new CreateScope {
      override val headers = Seq("Content-Length" -> "20", "Content-Disposition" -> "attachment; filename=foobar.txt")
      h.status(result)
      there was one(mockUploadIterateeFactory).apply(groupedFileUpload, 0L)
    }

    "create a GroupedUploadIteratee using Content-Range" in new CreateScope {
      override val headers = Seq("Content-Range" -> "bytes 10-19/20", "Content-Disposition" -> "attachment; filename=foobar.txt")
      h.status(result)
      there was one(mockUploadIterateeFactory).apply(groupedFileUpload, 10L)
    }

    "feed data to the enumerator" in new CreateScope {
      val buf = new ArrayBuffer[Byte]
      override val enumerator = Enumerator("12345".getBytes("utf-8"), "23456".getBytes("utf-8"))
      mockUploadIterateeFactory(any, any) returns Iteratee.foreach(bytes => buf.appendAll(bytes))
      h.status(result)
      buf.toArray must beEqualTo("1234523456".getBytes("utf-8"))
    }

    "return Ok" in new CreateScope {
      h.status(result) must beEqualTo(h.CREATED)
    }
  }
}
