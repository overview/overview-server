package controllers.util

import java.util.UUID
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.concurrent.Execution
import play.api.libs.iteratee.Enumerator
import play.api.mvc.RequestHeader
import play.api.test.{FakeRequest,FutureAwaits,DefaultAwaitTimeout}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import controllers.backend.{FileGroupBackend,GroupedFileUploadBackend}
import org.overviewproject.models.{FileGroup, GroupedFileUpload}
import org.overviewproject.test.factories.PodoFactory

class MassUploadFileIterateeSpec extends Specification with Mockito with FutureAwaits with DefaultAwaitTimeout {
  sequential

  trait BaseScope extends Scope {
    val mockFileGroupBackend = smartMock[FileGroupBackend]
    val mockUploadBackend = smartMock[GroupedFileUploadBackend]

    val guid: UUID = UUID.randomUUID

    val iterateeFactory = new MassUploadFileIteratee {
      override val fileGroupBackend = mockFileGroupBackend
      override val groupedFileUploadBackend = mockUploadBackend
    }

    val factory = PodoFactory

    lazy val fileGroup = factory.fileGroup()
    lazy val groupedFileUpload = factory.groupedFileUpload(guid=guid)

    mockFileGroupBackend.findOrCreate(any) returns Future(fileGroup)
    mockUploadBackend.findOrCreate(any) returns Future(groupedFileUpload)
    mockUploadBackend.writeBytes(any, any, any) returns Future(())

    val userEmail: String = "user@example.org"
    val baseHeaders: Seq[(String,String)] = Seq(
      "Content-Type" -> "application/pdf",
      "Content-Disposition" -> "attachment; filename=foo.pdf"
    )
    def requestHeaders: Seq[(String,String)] = baseHeaders
    def request: RequestHeader = FakeRequest().withHeaders(requestHeaders: _*)
    val bufferSize: Int = 10

    def iteratee = iterateeFactory(userEmail, request, guid, bufferSize)

    def byteArrays: Seq[Array[Byte]] = Seq("1234567890".getBytes("utf-8"))
    def enumerator = Enumerator.enumerate(byteArrays)

    def run = enumerator.run(iteratee)
  }

  "MassUploadFileIteratee" should {
    "fail if there is neither Content-Range nor Content-Length" in new BaseScope {
      await(run).toString must beEqualTo(MassUploadFileIteratee.BadRequest("Request did not specify Content-Range or Content-Length").toString)
      there was no(mockUploadBackend).writeBytes(any, any, any)
    }

    "work with Content-Length" in new BaseScope {
      override def requestHeaders = baseHeaders :+ ("Content-Length" -> "10")
      await(run).toString must beEqualTo(MassUploadFileIteratee.Ok.toString)
    }

    "work with Content-Range" in new BaseScope {
      override def requestHeaders = baseHeaders :+ ("Content-Range" -> "bytes 1-10/10")
      await(run).toString must beEqualTo(MassUploadFileIteratee.Ok.toString)
    }

    "create a FileGroup is there is none" in new BaseScope {
      override def requestHeaders = baseHeaders :+ ("Content-Length" -> "10")
      await(run)
      there was one(mockFileGroupBackend).findOrCreate(FileGroup.CreateAttributes(userEmail, None))
    }

    "create a GroupedFileUpload if there is none" in new BaseScope {
      override def requestHeaders = baseHeaders :+ ("Content-Length" -> "10")
      await(run)
      there was one(mockUploadBackend).findOrCreate(GroupedFileUpload.CreateAttributes(fileGroup.id, guid, "application/pdf", "foo.pdf", 10L))
    }

    "write bytes to the GroupedFileUpload" in new BaseScope {
      override def requestHeaders = baseHeaders :+ ("Content-Length" -> "10")
      await(run)
      there was one(mockUploadBackend).writeBytes(groupedFileUpload.id, 0L, "1234567890".getBytes("utf-8"))
    }

    "write multiple chunks" in new BaseScope {
      override def requestHeaders = baseHeaders :+ ("Content-Length" -> "20")
      override def byteArrays = Seq("1234567890".getBytes("utf-8"), "0987654321".getBytes("utf-8"))
      await(run)
      there was one(mockUploadBackend).writeBytes(groupedFileUpload.id, 0L, "1234567890".getBytes("utf-8"))
      there was one(mockUploadBackend).writeBytes(groupedFileUpload.id, 10L, "0987654321".getBytes("utf-8"))
    }

    "buffer chunks" in new BaseScope {
      override def requestHeaders = baseHeaders :+ ("Content-Length" -> "20")
      override def byteArrays = Seq("123".getBytes("utf-8"), "45".getBytes("utf-8"), "67890".getBytes("utf-8"), "0987654321".getBytes("utf-8"))
      await(run)
      there was one(mockUploadBackend).writeBytes(groupedFileUpload.id, 0L, "1234567890".getBytes("utf-8"))
      there was one(mockUploadBackend).writeBytes(groupedFileUpload.id, 10L, "0987654321".getBytes("utf-8"))
    }

    "buffer chunks even when sizes don't fit into each other perfectly" in new BaseScope {
      override def requestHeaders = baseHeaders :+ ("Content-Length" -> "19")
      override def byteArrays = Seq("1234567".getBytes("utf-8"), "890098".getBytes("utf-8"), "765432".getBytes("utf-8"))
      await(run)
      there was one(mockUploadBackend).writeBytes(groupedFileUpload.id, 0L, "1234567890".getBytes("utf-8"))
      there was one(mockUploadBackend).writeBytes(groupedFileUpload.id, 10L, "098765432".getBytes("utf-8"))
    }

    "use empty strings for missing headers" in new BaseScope {
      override def requestHeaders = Seq("Content-Length" -> "10")
      await(run)
      there was one(mockUploadBackend).findOrCreate(GroupedFileUpload.CreateAttributes(fileGroup.id, guid, "", "", 10L))
    }

    "fail if appending data fails" in new BaseScope {
      override def requestHeaders = baseHeaders :+ ("Content-Length" -> "10")
      mockUploadBackend.writeBytes(any, any, any) returns Future(throw new Exception("random"))
      await(run) must throwA[Exception]
    }

    "append data at start byte" in new BaseScope {
      override lazy val groupedFileUpload = factory.groupedFileUpload(size=20L, uploadedSize=10L)
      override def requestHeaders = baseHeaders ++ Seq(
        "Content-Range" -> "bytes 10-19/20",
        "Content-Length" -> "10"
      )

      await(run).toString must beEqualTo(MassUploadFileIteratee.Ok.toString)
      there was one(mockUploadBackend).writeBytes(groupedFileUpload.id, 10L, "1234567890".getBytes("utf-8"))
    }

    "overwrite data at start byte" in new BaseScope {
      override lazy val groupedFileUpload = factory.groupedFileUpload(size=20L, uploadedSize=11L)
      override def requestHeaders = baseHeaders ++ Seq(
        "Content-Range" -> "bytes 10-19/20",
        "Content-Length" -> "10"
      )

      await(run).toString must beEqualTo(MassUploadFileIteratee.Ok.toString)
      there was one(mockUploadBackend).writeBytes(groupedFileUpload.id, 10L, "1234567890".getBytes("utf-8"))
    }

    "return an error when start of content range is too high" in new BaseScope {
      override lazy val groupedFileUpload = factory.groupedFileUpload(size=20L, uploadedSize=9L)
      override def requestHeaders = baseHeaders ++ Seq(
        "Content-Range" -> "bytes 11-19/20",
        "Content-Length" -> "10"
      )

      await(run).toString must beEqualTo(MassUploadFileIteratee.BadRequest("Tried to resume past last uploaded byte. Resumed at byte 11, but only 9 bytes have been uploaded.").toString)
      there was no(mockUploadBackend).writeBytes(any, any, any)
    }

    "succeed with zero-length requests" in new BaseScope {
      override lazy val groupedFileUpload = factory.groupedFileUpload(size=0L, uploadedSize=0L)
      override def requestHeaders = baseHeaders ++ Seq(
        "Content-Length" -> "0"
      )
      override def byteArrays = Seq(Array[Byte]())
      await(run).toString must beEqualTo(MassUploadFileIteratee.Ok.toString)
      there was no(mockUploadBackend).writeBytes(any, any, any)
    }
  }
}
