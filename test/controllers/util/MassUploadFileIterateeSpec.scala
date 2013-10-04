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

class MassUploadFileIterateeSpec extends Specification with Mockito {

  "MassUploadFileIteratee" should {

    class TestMassUploadFileIteratee extends MassUploadFileIteratee {
      override val storage = smartMock[Storage]

      val fileUpload = smartMock[GroupedFileUpload]
      val fileGroup = smartMock[FileGroup]
      fileGroup.id returns 1l

      storage.findCurrentFileGroup returns Some(fileGroup)
      storage.createUpload(any, any, any, any) returns fileUpload
      storage.appendData(any, any) returns fileUpload
    }

    trait UploadContext extends Scope {
      val contentType = "ignoredForNow"
      val filename = "filename.ext"
      val contentDisposition = s"attachement; filename=$filename"
      val start = 0
      val end = 255
      val total = 256
      val bufferSize = total
      
      val data = Array.tabulate[Byte](256)(_.toByte)
      
      val iteratee = new TestMassUploadFileIteratee

      val input = new ByteArrayInputStream(data)
      val enumerator: Enumerator[Array[Byte]]

      val headers: Seq[(String, Seq[String])] = Seq(
        (CONTENT_TYPE, Seq(contentType)),
        (CONTENT_RANGE, Seq(s"$start-$end/$total")),
        (CONTENT_LENGTH, Seq(s"$total")),
        (CONTENT_DISPOSITION, Seq(contentDisposition)))

      val request: RequestHeader = {
        val r = mock[RequestHeader]
        r.headers returns FakeHeaders(headers)
        r
      }

      def result = {
        val resultFuture = enumerator.run(iteratee(request, bufferSize))
        Await.result(resultFuture, Duration.Inf)
      }
    }

    trait SingleChunkUpload extends UploadContext {
      override val enumerator = Enumerator.fromStream(input)
    }
    
    trait MultipleChunksUpload extends UploadContext {
      val chunkSize = 100
      override val bufferSize = chunkSize
      override val enumerator = Enumerator.fromStream(input, chunkSize)
    }
    
    trait BufferedUpload extends UploadContext {
      val chunkSize = 64
      override val bufferSize = 150
      override val enumerator = Enumerator.fromStream(input, chunkSize)
    }

    "produce a MassUploadFile" in new SingleChunkUpload {
      result must beRight

      there was one(iteratee.storage).findCurrentFileGroup
      there was one(iteratee.storage).createUpload(1l, contentType, filename, total)
      there was one(iteratee.storage).appendData(iteratee.fileUpload, data)
    }

    "handle chunked input" in new MultipleChunksUpload {
      result must beRight
      
      there was one(iteratee.storage).appendData(iteratee.fileUpload, data.slice(0, chunkSize))
      there was one(iteratee.storage).appendData(iteratee.fileUpload, data.slice(chunkSize, 2 * chunkSize))
      there was one(iteratee.storage).appendData(iteratee.fileUpload, data.slice(2 * chunkSize, total))
    }
    
    "buffer chunks" in new BufferedUpload {
      result must beRight
      
      there was one(iteratee.storage).appendData(iteratee.fileUpload, data.slice(0, 192))
      there was one(iteratee.storage).appendData(iteratee.fileUpload, data.slice(192, 256))
    }
  }
}