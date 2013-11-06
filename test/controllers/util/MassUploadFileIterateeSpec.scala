package controllers.util

import java.io.ByteArrayInputStream
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Random
import play.api.libs.iteratee.Enumerator
import org.overviewproject.tree.orm.{ FileGroup, GroupedFileUpload }
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.mvc.RequestHeader
import play.api.test.FakeHeaders
import play.api.test.Helpers._
import java.util.UUID
import java.net.URLEncoder

class MassUploadFileIterateeSpec extends Specification with Mockito {

  "MassUploadFileIteratee" should {

    abstract class TestMassUploadFileIteratee extends MassUploadFileIteratee {
      val fileUpload = smartMock[GroupedFileUpload]
    }

    class SucceedingMassUploadFileIteratee(createFileGroup: Boolean) extends TestMassUploadFileIteratee {
      override val storage = smartMock[Storage]

      val fileGroup = smartMock[FileGroup]
      fileGroup.id returns 1l

      if (createFileGroup) {
        storage.findCurrentFileGroup(any) returns None
        storage.createFileGroup(any) returns fileGroup
      } else storage.findCurrentFileGroup(any) returns Some(fileGroup)

      storage.findUpload(any, any) returns None
      storage.createUpload(any, any, any, any, any) returns fileUpload
      storage.appendData(any, any) returns fileUpload
    }

    class FailingMassUploadFileIteratee extends TestMassUploadFileIteratee {
      override val storage = smartMock[Storage]

      val fileGroup = smartMock[FileGroup]
      fileGroup.id returns 1l

      storage.findCurrentFileGroup(any) returns Some(fileGroup)

      storage.findUpload(any, any) returns None
      storage.createUpload(any, any, any, any, any) returns fileUpload
      storage.appendData(any, any) throws new RuntimeException("append failed")
    }

    trait FileGroupProvider {
      val createFileGroup: Boolean
    }

    trait Headers {
      val start = 0
      val end = 255
      val total = 256

      val headers: Seq[(String, Seq[String])]
    }

    trait UploadContext extends Scope with FileGroupProvider with Headers {
      val userEmail = "user@ema.il"
      val bufferSize: Int
      val guid = UUID.randomUUID

      val data = Array.tabulate[Byte](256)(_.toByte)

      val input = new ByteArrayInputStream(data)
      val enumerator: Enumerator[Array[Byte]]

      def createRequest: RequestHeader = {
        val r = mock[RequestHeader]
        r.headers returns FakeHeaders(headers)
        r
      }

      lazy val iteratee: TestMassUploadFileIteratee = new SucceedingMassUploadFileIteratee(createFileGroup)

      def result = {
        val resultFuture = enumerator.run(iteratee(userEmail, createRequest, guid, bufferSize))
        Await.result(resultFuture, Duration.Inf)
      }
    }

    trait FailingUploadContext extends UploadContext {
      override lazy val iteratee: TestMassUploadFileIteratee = new FailingMassUploadFileIteratee
      override val bufferSize = total
      override val enumerator = Enumerator.fromStream(input)
      override val createFileGroup = false
    }

    trait GoodHeaders extends Headers {
      val contentType = "ignoredForNow"
      val filename = "filename.ext"
      val contentDisposition = s"""attachment; filename=$filename"""

      override val headers: Seq[(String, Seq[String])] = Seq(
        (CONTENT_TYPE, Seq(contentType)),
        (CONTENT_RANGE, Seq(s"$start-$end/$total")),
        (CONTENT_LENGTH, Seq(s"$total")),
        (CONTENT_DISPOSITION, Seq(contentDisposition)))
    }

    trait MissingOptionalHeaders extends Headers {
      override val headers: Seq[(String, Seq[String])] = Seq(
        (CONTENT_RANGE, Seq(s"$start-$end/$total")))
    }

    trait ExistingFileGroup extends FileGroupProvider {
      override val createFileGroup: Boolean = false
    }

    trait NoFileGroup extends FileGroupProvider {
      override val createFileGroup: Boolean = true
    }

    trait SingleChunkUpload extends UploadContext with GoodHeaders {
      override val bufferSize = total
      override val enumerator = Enumerator.fromStream(input)
    }

    trait MultipleChunksUpload extends UploadContext with GoodHeaders {
      val chunkSize = 100
      override val bufferSize = chunkSize
      override val enumerator = Enumerator.fromStream(input, chunkSize)
    }

    trait BufferedUpload extends UploadContext with GoodHeaders {
      val chunkSize = 64
      override val bufferSize = 150
      override val enumerator = Enumerator.fromStream(input, chunkSize)
    }

    trait UploadWithMissingHeaders extends UploadContext with MissingOptionalHeaders {
      override val bufferSize = total
      override val enumerator = Enumerator.fromStream(input)
    }

    "create a FileGroup if there is none" in new SingleChunkUpload with NoFileGroup {
      result must beRight

      there was one(iteratee.storage).createFileGroup(userEmail)
    }

    "produce a MassUploadFile" in new SingleChunkUpload with ExistingFileGroup {
      result must beRight

      there was one(iteratee.storage).findCurrentFileGroup(userEmail)
      there was one(iteratee.storage).createUpload(1l, contentType, filename, guid, total)
      there was one(iteratee.storage).appendData(iteratee.fileUpload, data)
    }

    "handle chunked input" in new MultipleChunksUpload with ExistingFileGroup {
      result must beRight

      there was one(iteratee.storage).appendData(iteratee.fileUpload, data.slice(0, chunkSize))
      there was one(iteratee.storage).appendData(iteratee.fileUpload, data.slice(chunkSize, 2 * chunkSize))
      there was one(iteratee.storage).appendData(iteratee.fileUpload, data.slice(2 * chunkSize, total))
    }

    "buffer chunks" in new BufferedUpload with ExistingFileGroup {
      result must beRight

      there was one(iteratee.storage).appendData(iteratee.fileUpload, data.slice(0, 192))
      there was one(iteratee.storage).appendData(iteratee.fileUpload, data.slice(192, 256))
    }

    "Use empty strings for missing optional headers" in new UploadWithMissingHeaders with ExistingFileGroup {
      result must beRight

      there was one(iteratee.storage).createUpload(1l, "", "", guid, total)
    }

    "Return an error result if appending data fails" in new FailingUploadContext with GoodHeaders {
      result must beLeft
    }
  }
}