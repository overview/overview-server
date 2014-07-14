package controllers.util

import java.io.ByteArrayInputStream
import java.net.URLEncoder
import java.util.UUID
import org.mockito.ArgumentCaptor
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.concurrent.Execution
import play.api.libs.iteratee.Enumerator
import play.api.mvc.{RequestHeader, Result}
import play.api.mvc.Results._
import play.api.test.{ FakeApplication, FakeHeaders }
import play.api.test.Helpers._
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Random
import play.api.Play.{ start, stop }

import views.html.defaultpages.badRequest
import org.overviewproject.tree.orm.{ FileGroup, GroupedFileUpload }

class MassUploadFileIterateeSpec extends Specification with Mockito {
  step(start(FakeApplication()))
  
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
      val fileGroup = smartMock[FileGroup]
      fileGroup.id returns fileGroupId
    }

    class SucceedingMassUploadFileIteratee(createFileGroup: Boolean) extends TestMassUploadFileIteratee {

      if (createFileGroup) {
        storage.findCurrentFileGroup(any) returns None
        storage.createFileGroup(any) returns fileGroup
      } else storage.findCurrentFileGroup(any) returns Some(fileGroup)

      storage.findUpload(any, any) returns None
      storage.createUpload(fileGroupId, contentType, filename, guid, total) returns fileUpload

    }

    class FailingMassUploadFileIteratee extends TestMassUploadFileIteratee {

      storage.findCurrentFileGroup(any) returns Some(fileGroup)

      storage.findUpload(any, any) returns None
      storage.createUpload(fileGroupId, contentType, filename, guid, total) returns fileUpload
      storage.appendData(any, any) throws new RuntimeException("append failed")
    }

    class RestartingMassUploadFileIteratee extends TestMassUploadFileIteratee {
      storage.findCurrentFileGroup(any) returns Some(fileGroup)
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
      override val enumerator = Enumerator.fromStream(input)(Execution.defaultContext)
      override val createFileGroup = false
    }

    trait RestartingUploadContext extends Scope with Headers {
      val bufferSize: Int

      val data = Array.tabulate[Byte](total)(_.toByte)

      val input = new ByteArrayInputStream(data)
      val enumerator: Enumerator[Array[Byte]]

      def createRequest: RequestHeader = {
        val r = mock[RequestHeader]
        r.headers returns FakeHeaders(headers)
        r
      }

      lazy val iteratee: TestMassUploadFileIteratee = new RestartingMassUploadFileIteratee

      def result = {
        val resultFuture = enumerator.run(iteratee(userEmail, createRequest, guid, bufferSize))
        Await.result(resultFuture, Duration.Inf)
      }

    }

    trait GoodHeaders extends Headers {

      override val headers: Seq[(String, Seq[String])] = Seq(
        (CONTENT_TYPE, Seq(contentType)),
        (CONTENT_RANGE, Seq(s"bytes $start-$end/$total")),
        (CONTENT_LENGTH, Seq(s"$total")),
        (CONTENT_DISPOSITION, Seq(contentDisposition)))
    }

    trait MissingOptionalHeaders extends Headers {
      override val headers: Seq[(String, Seq[String])] = Seq(
        (CONTENT_RANGE, Seq(s"bytes $start-$end/$total")))
    }

    trait RestartHeaders extends Headers {
      val restart = 100

      override val headers: Seq[(String, Seq[String])] = Seq(
        (CONTENT_TYPE, Seq(contentType)),
        (CONTENT_RANGE, Seq(s"bytes $restart-$end/$total")),
        (CONTENT_LENGTH, Seq(s"${total - restart}")),
        (CONTENT_DISPOSITION, Seq(contentDisposition)))

    }
    trait ExistingFileGroup extends FileGroupProvider {
      override val createFileGroup: Boolean = false
    }

    trait NoFileGroup extends FileGroupProvider {
      override val createFileGroup: Boolean = true
    }

    trait SingleChunkUpload extends UploadContext with GoodHeaders {
      override val bufferSize = total
      override val enumerator = Enumerator.fromStream(input)(Execution.defaultContext)
    }

    trait MultipleChunksUpload extends UploadContext with GoodHeaders {
      val chunkSize = 100
      override val bufferSize = chunkSize
      override val enumerator = Enumerator.fromStream(input, chunkSize)(Execution.defaultContext)
    }

    trait BufferedUpload extends UploadContext with GoodHeaders {
      val chunkSize = 64
      override val bufferSize = 150
      override val enumerator = Enumerator.fromStream(input, chunkSize)(Execution.defaultContext)
    }

    trait UploadWithMissingHeaders extends UploadContext with MissingOptionalHeaders {
      override val bufferSize = total
      override val enumerator = Enumerator.fromStream(input)(Execution.defaultContext)
    }

    trait RestartContext extends RestartingUploadContext with RestartHeaders {
      override val bufferSize = total
      override val enumerator = Enumerator.fromStream(input)(Execution.defaultContext)
    }

    "create a FileGroup if there is none" in new SingleChunkUpload with NoFileGroup {
      iteratee.storage.appendData(any, any) returns fileUpload.copy(uploadedSize = total)

      result must beRight

      there was one(iteratee.storage).createFileGroup(userEmail)
    }

    "produce a MassUploadFile" in new SingleChunkUpload with ExistingFileGroup {
      iteratee.storage.appendData(any, any) returns fileUpload.copy(uploadedSize = total)

      result must beRight

      val upload = ArgumentCaptor.forClass(classOf[GroupedFileUpload])
      val chunk = ArgumentCaptor.forClass(classOf[Iterable[Byte]])

      there was one(iteratee.storage).findCurrentFileGroup(userEmail)
      there was one(iteratee.storage).createUpload(1l, contentType, filename, guid, total)
      there was one(iteratee.storage).appendData(upload.capture, chunk.capture)

      upload.getValue.guid must be equalTo guid
      chunk.getValue must be equalTo data
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
      iteratee.storage.createUpload(fileGroupId, "", "", guid, total) returns fileUpload
      iteratee.storage.appendData(any, any) returns fileUpload
      result must beRight

      there was one(iteratee.storage).createUpload(1l, "", "", guid, total)
    }

    "Return an error result if appending data fails" in new FailingUploadContext with GoodHeaders {
      result must beLeft
    }.pendingUntilFixed // possibly scala bug? https://github.com/scala/scala/pull/3082

    "append data at start byte if less than uploadedSize" in new RestartContext {
      iteratee.storage.findUpload(fileGroupId, guid) returns Some(fileUpload.copy(uploadedSize = restart + 10))

      iteratee.storage.appendData(any, any) returns fileUpload.copy(uploadedSize = total)

      result must beRight

      val upload = ArgumentCaptor.forClass(classOf[GroupedFileUpload])
      val chunk = ArgumentCaptor.forClass(classOf[Iterable[Byte]])

      there was one(iteratee.storage).appendData(upload.capture, chunk.capture)

      upload.getValue.uploadedSize must be equalTo restart
    }

    "return an error if start of content range is past uploadedSize" in new RestartContext {
      iteratee.storage.findUpload(fileGroupId, guid) returns Some(fileUpload.copy(uploadedSize = restart - 10))

      result must beLeft((r: Result) => r.header.status must beEqualTo(BAD_REQUEST))
    }

    "succeed if request has zero length" in new UploadContext {
      override val bufferSize = 10
      override val enumerator = Enumerator.eof[Array[Byte]]
      override val headers = Seq(
        CONTENT_LENGTH -> Seq("0"),
        CONTENT_DISPOSITION -> Seq(contentDisposition),
        CONTENT_TYPE -> Seq(contentType)
      )
      override val createFileGroup = false

      override lazy val iteratee = new TestMassUploadFileIteratee {
        storage.findCurrentFileGroup(any) returns Some(fileGroup)
        storage.findUpload(any, any) returns None
        storage.createUpload(fileGroupId, contentType, filename, guid, 0L) returns GroupedFileUpload(
          fileGroupId=1L,
          guid=guid,
          contentType=contentType,
          name=filename,
          size=0L,
          uploadedSize=0L,
          contentsOid=0L,
          id=1L
        )
      }

      result must beRight
    }
  }
  
  step(stop)
}
