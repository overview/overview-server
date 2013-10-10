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

    class TestMassUploadFileIteratee(createFileGroup: Boolean) extends MassUploadFileIteratee {
      override val storage = smartMock[Storage]

      val fileGroup = smartMock[FileGroup]
      fileGroup.id returns 1l
      
      val fileUpload = smartMock[GroupedFileUpload]

      if (createFileGroup) {
    	  storage.findCurrentFileGroup(any) returns None
    	  storage.createFileGroup(any) returns fileGroup
      }
      else storage.findCurrentFileGroup(any) returns Some(fileGroup)
      
      storage.createUpload(any, any, any, any, any) returns fileUpload
      storage.appendData(any, any) returns fileUpload
    }
    
    trait FileGroupProvider {
      val createFileGroup: Boolean
    }

    trait UploadContext extends Scope with FileGroupProvider {
      val userEmail = "user@ema.il"
      val contentType = "ignoredForNow"
      val filename = "filename.ext"
      val contentDisposition = s"""attachment; filename=$filename"""
      val start = 0
      val end = 255
      val total = 256
      val bufferSize = total
      val guid = UUID.randomUUID
        
      val data = Array.tabulate[Byte](256)(_.toByte)
      
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

      lazy val iteratee = new TestMassUploadFileIteratee(createFileGroup)

      def result = {
        val resultFuture = enumerator.run(iteratee(userEmail, request, guid, bufferSize))
        Await.result(resultFuture, Duration.Inf)
      }
    }

    trait ExistingFileGroup extends FileGroupProvider {
      override val createFileGroup: Boolean = false
    }
    
    trait NoFileGroup extends FileGroupProvider {
      override val createFileGroup: Boolean = true
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
  }
}