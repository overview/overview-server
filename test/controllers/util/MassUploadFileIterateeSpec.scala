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
import org.mockito.ArgumentCaptor

class MassUploadFileIterateeSpec extends Specification with Mockito {

  "MassUploadFileIteratee" should {
    val start = 0
    val end = 255
    val total = 256
    val guid = UUID.randomUUID
    val userEmail = "user@ema.il"
    val contentType = "ignoredForNow"
    val filename = "filename.ext"
    val contentDisposition = s"""attachment; filename=$filename"""
    val fileGroupId = 1l
    val fileUpload = GroupedFileUpload(1l, guid, contentType, filename, total, 0, 1)
    val fileUpload2 = GroupedFileUpload(1l, guid, contentType, filename, total, 100, 1)

    abstract class TestMassUploadFileIteratee extends MassUploadFileIteratee {
      override val storage = smartMock[Storage]
    }

    class SucceedingMassUploadFileIteratee(createFileGroup: Boolean) extends TestMassUploadFileIteratee {

      val fileGroup = smartMock[FileGroup]
      fileGroup.id returns 1l

      if (createFileGroup) {
        storage.findCurrentFileGroup(any) returns None
        storage.createFileGroup(any) returns fileGroup
      } else storage.findCurrentFileGroup(any) returns Some(fileGroup)

      storage.findUpload(any, any) returns None
      storage.createUpload(fileGroupId, contentType, filename, guid, total) returns fileUpload

    }

    class FailingMassUploadFileIteratee extends TestMassUploadFileIteratee {

      val fileGroup = smartMock[FileGroup]
      fileGroup.id returns 1l

      storage.findCurrentFileGroup(any) returns Some(fileGroup)

      storage.findUpload(any, any) returns None
      storage.createUpload(fileGroupId, contentType, filename, guid, total) returns null
      storage.appendData(any, any) throws new RuntimeException("append failed")
    }

    trait FileGroupProvider {
      val createFileGroup: Boolean
    }

    trait Headers {
      val headers: Seq[(String, Seq[String])]
    }

    trait UploadContext extends Scope with FileGroupProvider with Headers {
      val bufferSize: Int

      val data = Array.tabulate[Byte](total)(_.toByte)

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
      iteratee.storage.appendData(any, any) returns fileUpload.copy(uploadedSize = total)

      result must beRight

      there was one(iteratee.storage).createFileGroup(userEmail)
    }

    "produce a MassUploadFile" in new SingleChunkUpload with ExistingFileGroup {
      iteratee.storage.appendData(any, any) returns fileUpload.copy(uploadedSize = total)

      result must beRight

      there was one(iteratee.storage).findCurrentFileGroup(userEmail)
      there was one(iteratee.storage).createUpload(1l, contentType, filename, guid, total)
      there was one(iteratee.storage).appendData(fileUpload, data)
    }

    "handle chunked input" in new MultipleChunksUpload with ExistingFileGroup {
      iteratee.storage.appendData(any, any) returns
        fileUpload.copy(uploadedSize = chunkSize) thenReturns
        fileUpload.copy(uploadedSize = 2 * chunkSize) thenReturns
        fileUpload.copy(uploadedSize = total)

      result must beRight

      val upload = ArgumentCaptor.forClass(classOf[GroupedFileUpload])
      val chunk = ArgumentCaptor.forClass(classOf[Iterable[Byte]])

      there were three(iteratee.storage).appendData(upload.capture, chunk.capture)

      chunk.getAllValues().get(0) must be equalTo (data.slice(0, chunkSize))
      chunk.getAllValues().get(1) must be equalTo (data.slice(chunkSize, 2 * chunkSize))
      chunk.getAllValues().get(2) must be equalTo (data.slice(2 * chunkSize, total))

      upload.getValue().guid must be equalTo (guid)
      upload.getAllValues().get(1).uploadedSize must be equalTo (chunkSize)
      upload.getAllValues().get(2).uploadedSize must be equalTo (2 * chunkSize)
    }

    "buffer chunks" in new BufferedUpload with ExistingFileGroup {
      iteratee.storage.appendData(any, any) returns
        fileUpload.copy(uploadedSize = 192) thenReturns
        fileUpload.copy(uploadedSize = 256)

      result must beRight

      val upload = ArgumentCaptor.forClass(classOf[GroupedFileUpload])
      val chunk = ArgumentCaptor.forClass(classOf[Iterable[Byte]])

      there were two(iteratee.storage).appendData(upload.capture, chunk.capture)

      chunk.getAllValues().get(0) must be equalTo (data.slice(0, 192))
      chunk.getAllValues().get(1) must be equalTo (data.slice(192, 256))
    }

    "Use empty strings for missing optional headers" in new UploadWithMissingHeaders with ExistingFileGroup {
      result must beRight

      there was one(iteratee.storage).createUpload(1l, "", "", guid, total)
    }

    "Return an error result if appending data fails" in new FailingUploadContext with GoodHeaders {
      result must beLeft
    }.pendingUntilFixed // possibly scala bug? https://github.com/scala/scala/pull/3082
  }
}