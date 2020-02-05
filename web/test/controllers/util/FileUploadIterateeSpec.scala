package controllers.util

import akka.stream.scaladsl.{Source,StreamConverters}
import akka.util.ByteString
import java.io.ByteArrayInputStream
import java.sql.{SQLException, Timestamp}
import java.util.UUID
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import play.api.libs.iteratee.Enumerator
import play.api.mvc.{RequestHeader, Result}
import play.api.test.{FakeHeaders, FakeRequest}
import play.api.test.Helpers._
import scala.concurrent.Await
import scala.util.Random

import models.upload.{OverviewUpload, OverviewUploadedFile}

class FileUploadIterateeSpec extends test.helpers.InAppSpecification with Mockito {
  "FileUploadIteratee" should {

    /** OverviewUpload implementation that stores data in an attribute */
    case class DummyUpload(userId: Long, guid: UUID, val bytesUploaded: Long, val size: Long, 
      var data: Array[Byte] = Array[Byte](), uploadedFile: OverviewUploadedFile = mock[OverviewUploadedFile]) extends OverviewUpload {

      val id = 1231234L
      val lastActivity: Timestamp = new Timestamp(0)
      val contentsOid: Long = 1l


      def upload(chunk: Array[Byte]): DummyUpload = {
        data = data ++ chunk
        uploadedFile.size returns (data.size)

        withUploadedBytes(data.size)
      }

      def withUploadedBytes(bytesUploaded: Long): DummyUpload = this.copy(bytesUploaded = bytesUploaded)

      def save: DummyUpload = this
      def truncate: DummyUpload = { this.copy(bytesUploaded = 0, data = Array[Byte]()) }
      def delete {}
      def underlying = ???
    }

    /**
     * Implementation of FileUploadIteratee for testing, avoiding using the database
     */
    class TestIteratee(throwOnCancel: Boolean = false) extends FileUploadIteratee {

      // store the upload as DummyUpload to avoid need for downcasting
      var currentUpload: DummyUpload = _

      var appendCount: Int = 0
      var uploadCancelled: Boolean = false
      var uploadTruncated: Boolean = false

      def findUpload(userId: Long, guid: UUID): Option[OverviewUpload] = Option(currentUpload)

      def createUpload(userId: Long, guid: UUID, contentDisposition: String, contentType: String, contentLength: Long): OverviewUpload = {
        uploadCancelled = false
        val uploadedFile = mock[OverviewUploadedFile]
        uploadedFile.contentDisposition returns contentDisposition
        uploadedFile.contentType returns contentType
        
        currentUpload = DummyUpload(userId, guid, 0l, contentLength, uploadedFile = uploadedFile)
        currentUpload
      }

      def cancelUpload(upload: OverviewUpload) {
        if (!throwOnCancel) {
          currentUpload = null
          uploadCancelled = true
        } else throw new SQLException()
      }

      def appendChunk(upload: OverviewUpload, chunk: Array[Byte]): OverviewUpload = {
        appendCount = appendCount + 1

        currentUpload = upload.asInstanceOf[DummyUpload].upload(chunk)
        currentUpload
      }

      def truncateUpload(upload: OverviewUpload): OverviewUpload = {
        uploadTruncated = true
        currentUpload.truncate
      }

      def uploadedData: Array[Byte] = currentUpload.data
    }

    trait UploadContext extends Scope {
      val digits100 = ByteString(Seq.fill(4)(Seq.range('a'.toByte, 'z'.toByte)).flatten.toArray)

      val userId = 1l
      val guid = UUID.randomUUID
      val uploadIteratee: TestIteratee

      val source: Source[ByteString, _] = Source.single(digits100) // 1 chunk, by default
      val bufferSize: Int = 20

      def request: RequestHeader
      // Drive the iteratee with the source to generate a result
      lazy val result: Either[Result, OverviewUpload] = {
        implicit val ec = scala.concurrent.ExecutionContext.Implicits.global

        val resultFuture = uploadIteratee.store(userId, guid, request, bufferSize).run(source)
        Await.result(resultFuture, scala.concurrent.duration.Duration.Inf)
      }

      def upload: OverviewUpload = result.right.get
    }

    // The tests specify:
    // - an Iteratee, that reacts in certain ways
    // - headers, different well-formed headers, as well as bad ones
    // - an enumarator that generates the data in different ways
    // Tests are setup by mixing traits that provide these three components
    // in different combinations

    // Iteraratees
    trait GoodUpload extends UploadContext {
      val uploadIteratee = new TestIteratee
    }

    trait FailingCancel extends UploadContext {
      val uploadIteratee = new TestIteratee(throwOnCancel = true)
    }

    // Headers
    trait UploadHeader {
      val contentDisposition = "attachment; filename=foo.bar"
      val contentType = "text/html; charset=ISO-8859-4"
        
      def headers: Seq[(String, String)]
      def request: RequestHeader = {
        val r = mock[RequestHeader]
        r.headers returns FakeHeaders(headers)

        r
      }
    }

    trait GoodHeader extends UploadHeader {
      def headers = Seq(
        (CONTENT_RANGE, "0-999/1000"),
        (CONTENT_DISPOSITION, contentDisposition),
        (CONTENT_LENGTH, "100"),
        (CONTENT_TYPE, contentType)  
      )
    }

    trait NoOptionalContentHeader extends UploadHeader {
      def headers = Seq(("Content-Range", "0-999/1000"))
    }

    trait BadHeader {
      def request: RequestHeader = FakeRequest()
    }

    trait InProgressHeader extends UploadHeader {
      def headers = Seq(
        (CONTENT_DISPOSITION, contentDisposition),
        (CONTENT_LENGTH, "100"),
        (CONTENT_RANGE, "100-199/1000")
        )
    }

    trait MalformedHeader extends UploadHeader {
      def headers = Seq(
        (CONTENT_DISPOSITION, contentDisposition),
        (CONTENT_RANGE, "Bad Field")
      )
    }

    "process Enumerator with one chunk only" in new GoodUpload with GoodHeader {
      result must beRight
      upload.uploadedFile.size must beEqualTo(100)
      uploadIteratee.uploadedData must beEqualTo(digits100.toArray)
    }

    "process multiple chunks" in new GoodUpload with GoodHeader {
      override val bufferSize = 20
      override val source = Source.fromIterator(() => digits100.grouped(20))
      result
      uploadIteratee.uploadedData must beEqualTo(digits100.toArray)
      uploadIteratee.appendCount must beEqualTo(5)
    }

    "buffer incoming data" in new GoodUpload with GoodHeader {
      override val bufferSize = 25
      override val source = Source.fromIterator(() => digits100.grouped(5))
      result
      upload.uploadedFile.size must beEqualTo(100)
      uploadIteratee.appendCount must beEqualTo(4)
    }

    "process last chunk of data if buffer not full at end of upload" in new GoodUpload with GoodHeader {
      override val bufferSize = 34 // 1st, 2nd: 34 bytes; 3rd: 32 bytes
      override val source = Source.fromIterator(() => digits100.grouped(34))
      result
      upload.uploadedFile.size must beEqualTo(100)
      uploadIteratee.appendCount must beEqualTo(3)
    }

    "truncate upload if restarted at byte 0" in new GoodUpload with GoodHeader {
      uploadIteratee.createUpload(userId, guid, "foo", "bar", 10)
      val initialUpload = upload
      val restartedUpload = upload

      result

      restartedUpload.uploadedFile.size must beEqualTo(100)
      uploadIteratee.uploadedData must beEqualTo(digits100.toArray)
      uploadIteratee.uploadTruncated must beTrue
    }

    "set contentDisposition to raw value of Content-Dispositon" in new GoodUpload with GoodHeader {
      upload.uploadedFile.contentDisposition must beEqualTo(contentDisposition)
    }
    
    "set contentType to raw value of Content-Type" in new GoodUpload with GoodHeader {
      upload.uploadedFile.contentType must beEqualTo(contentType)
    }

    "set content* to empty string if no header is found" in new GoodUpload with NoOptionalContentHeader {
      upload.uploadedFile.contentDisposition must beEqualTo("")
      upload.uploadedFile.contentType must beEqualTo("")
    }

    "return BAD_REQUEST if headers are bad" in new GoodUpload with BadHeader {
      result must beLeft.like { case r => r.header.status must beEqualTo(BAD_REQUEST) }
    }

    "return BAD_REQUEST and cancel upload if CONTENT_RANGE starts at the wrong byte" in new GoodUpload with InProgressHeader {
      uploadIteratee.createUpload(userId, guid, "foo", "bar", 1000)

      result must beLeft.like { case r => r.header.status must beEqualTo(BAD_REQUEST) }
      uploadIteratee.uploadCancelled must beTrue
    }

    "throw exception if cancel fails" in new FailingCancel with InProgressHeader {
      uploadIteratee.createUpload(userId, guid, "foo", "bar", 1000)

      result must throwA[SQLException]
    }

    "return Upload on valid restart" in new GoodUpload with InProgressHeader {
      val initialUpload = uploadIteratee.createUpload(userId, guid, "foo", "bar", 1000)
      uploadIteratee.appendChunk(initialUpload, digits100.toArray)

      result must beRight.like { case u => u.uploadedFile.size must beEqualTo(200) }
    }

    "return BAD_REQUEST if headers can't be parsed" in new GoodUpload with MalformedHeader {
      result must beLeft.like { case r => r.header.status must beEqualTo(BAD_REQUEST) }
    }
  }
}
