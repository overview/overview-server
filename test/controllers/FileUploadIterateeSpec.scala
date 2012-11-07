package controllers

import java.io.ByteArrayInputStream
import java.sql.Timestamp
import java.util.UUID
import scala.Array.canBuildFrom
import scala.util.Random
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import models.upload.OverviewUpload
import play.api.libs.concurrent.Promise
import play.api.libs.iteratee.Enumerator
import play.api.mvc.RequestHeader
import play.api.mvc.Result
import play.api.test.FakeHeaders
import play.api.test.FakeRequest
import play.api.test.Helpers._
import scala.Either.LeftProjection
import java.sql.SQLException

class FileUploadIterateeSpec extends Specification with Mockito {

  "FileUploadIteratee" should {

    /** OverviewUpload implementation that stores data in an attribute */
    case class TestUpload(userId: Long, guid: UUID, val bytesUploaded: Long, var data: Array[Byte] = Array[Byte]()) extends OverviewUpload {
      val lastActivity: Timestamp = new Timestamp(0)
      val contentsOid: Long = 1l

      def upload(chunk: Array[Byte]): TestUpload = {
        data = data ++ chunk
        withUploadedBytes(data.size)
      }

      def withUploadedBytes(bytesUploaded: Long): TestUpload = this.copy(bytesUploaded = bytesUploaded)

      def save: TestUpload = this
      def truncate: TestUpload = { this.copy(bytesUploaded = 0, data = Array[Byte]()) }
      def delete {}
    }

    /**
     * Implementation of FileUploadIteratee for testing, avoiding using the database
     */
    class TestIteratee(appendSucceeds: Boolean = true, throwOnCancel: Boolean = false) extends FileUploadIteratee {

      // store the upload as TestUpload to avoid need for downcasting
      var currentUpload: Option[TestUpload] = None

      var uploadCancelled: Boolean = false

      def findUpload(userId: Long, guid: UUID): Option[OverviewUpload] = currentUpload

      def createUpload(userId: Long, guid: UUID, filename: String, contentLength: Long): Option[OverviewUpload] = {
        uploadCancelled = false
        currentUpload = Some(TestUpload(userId, guid, 0l))
        currentUpload
      }

      def cancelUpload(upload: OverviewUpload) {
        if (!throwOnCancel) {
          currentUpload = None
          uploadCancelled = true
        } else throw new SQLException()
      }

      def appendChunk(upload: OverviewUpload, chunk: Array[Byte]): Option[OverviewUpload] = {
        if (appendSucceeds) {
          currentUpload = Some(upload.asInstanceOf[TestUpload].upload(chunk))
          currentUpload
        } else None
      }

      def uploadedData: Array[Byte] = currentUpload.map(_.data).orNull
    }

    trait UploadContext extends Scope {
      val chunk = new Array[Byte](100)
      Random.nextBytes(chunk)

      val userId = 1l
      val guid = UUID.randomUUID
      val uploadIteratee: TestIteratee

      def input = new ByteArrayInputStream(chunk)

      // implement enumerator in sub-classes to setup specific context
      def enumerator: Enumerator[Array[Byte]]

      def request: RequestHeader
      // Drive the iteratee with the enumerator to generate a result
      def result: Either[Result, OverviewUpload] = {
        val resultPromise = for {
          doneIt <- enumerator(uploadIteratee.store(userId, guid, request))
          result: Either[Result, OverviewUpload] <- doneIt.run
        } yield result
        resultPromise.await.get
      }
    }

    trait GoodUpload extends UploadContext {
      val uploadIteratee = new TestIteratee
    }

    trait FailingUpload extends UploadContext {
      val uploadIteratee = new TestIteratee(appendSucceeds = false)
    }

    trait FailingCancel extends UploadContext {
      val uploadIteratee = new TestIteratee(throwOnCancel = true)
    }

    trait GoodHeader {
      self: UploadContext =>
      def request: RequestHeader = {
        val r = mock[RequestHeader]
        r.headers returns FakeHeaders(Map(
          (CONTENT_RANGE, Seq("0-999/1000")),
          (CONTENT_DISPOSITION, Seq("attachment;filename=foo.bar")),
          (CONTENT_LENGTH, Seq("1000"))))

        r
      }

      def upload: OverviewUpload = result.right.get
    }

    trait MsHackHeader {
      self: UploadContext =>
      def request: RequestHeader = {
        val r = mock[RequestHeader]
        r.headers returns FakeHeaders(Map(
          ("X-MSHACK-Content-Range", Seq("0-999/1000")),
          (CONTENT_DISPOSITION, Seq("attachment;filename=foo.bar")),
          (CONTENT_LENGTH, Seq("1000"))))

        r
      }
      
      def upload: OverviewUpload = result.right.get
    }

    trait BadHeader {
      def request: RequestHeader = FakeRequest()
    }

    trait InProgressHeader {

      def request: RequestHeader = {
        val r = mock[RequestHeader]
        r.headers returns FakeHeaders(Map(
          (CONTENT_DISPOSITION, Seq("attachement;filename=foo.bar")),
          (CONTENT_LENGTH, Seq("1000")),
          (CONTENT_RANGE, Seq("100-199/1000"))))
      }
    }

    trait MalformedHeader {
      def request: RequestHeader = {
        val r = mock[RequestHeader]
        r.headers returns FakeHeaders(Map(
          (CONTENT_DISPOSITION, Seq("attachement;filename=foo.bar")),
          (CONTENT_LENGTH, Seq("Bad Field"))))
      }
    }

    trait SingleChunk {
      self: UploadContext =>
      def enumerator: Enumerator[Array[Byte]] = Enumerator.fromStream(input)
    }

    trait MultipleChunks {
      self: UploadContext =>
      def enumerator: Enumerator[Array[Byte]] = Enumerator.fromStream(input, 10)
    }

    "process Enumerator with one chunk only" in new GoodUpload with SingleChunk with GoodHeader {
      upload.bytesUploaded must be equalTo (chunk.size)
      uploadIteratee.uploadedData must be equalTo (chunk)
    }

    "process Enumerator with multiple chunks" in new GoodUpload with MultipleChunks with GoodHeader {
      upload.bytesUploaded must be equalTo (chunk.size)
      uploadIteratee.uploadedData must be equalTo (chunk)
    }

    "truncate upload if restarted at byte 0" in new GoodUpload with SingleChunk with GoodHeader {
      val initialUpload = upload
      val restartedUpload = upload

      restartedUpload.bytesUploaded must be equalTo (chunk.size)
      uploadIteratee.uploadedData must be equalTo (chunk)
    }

    "use X-MSHACK-Content-Range if Content-Range header not specified" in new GoodUpload with SingleChunk with MsHackHeader {
      upload.bytesUploaded must be equalTo (chunk.size)
    }

    "return BAD_REQUEST if headers are bad" in new GoodUpload with SingleChunk with BadHeader {
      result must beLeft.like { case r => status(r) must be equalTo (BAD_REQUEST) }
    }

    "return BAD_REQUEST and cancel upload if CONTENT_RANGE starts at the wrong byte" in new GoodUpload with SingleChunk with InProgressHeader {
      uploadIteratee.createUpload(userId, guid, "foo", 1000)

      result must beLeft.like { case r => status(r) must be equalTo (BAD_REQUEST) }
      uploadIteratee.uploadCancelled must beTrue
    }

    "not throw exception if cancel fails" in new FailingCancel with SingleChunk with InProgressHeader {
      uploadIteratee.createUpload(userId, guid, "foo", 1000)

      result must not(throwA[SQLException])
    }

    "return Upload on valid restart" in new GoodUpload with SingleChunk with InProgressHeader {
      val initialUpload = uploadIteratee.createUpload(userId, guid, "foo", 1000).get
      uploadIteratee.appendChunk(initialUpload, chunk)

      result must beRight.like { case u => u.bytesUploaded must be equalTo (200) }
    }

    "return INTERNAL_SERVER_ERROR if error occurs during upload" in new FailingUpload with MultipleChunks with GoodHeader {
      result must beLeft.like { case r => status(r) must be equalTo (INTERNAL_SERVER_ERROR) }
    }

    "return BAD_REQUEST if headers can't be parsed" in new GoodUpload with SingleChunk with MalformedHeader {
      result must beLeft.like { case r => status(r) must be equalTo (BAD_REQUEST) }
    }

  }
}
