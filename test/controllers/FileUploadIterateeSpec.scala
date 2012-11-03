package controllers

import java.io.ByteArrayInputStream
import java.sql.Timestamp
import java.util.UUID
import models.upload.OverviewUpload
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.concurrent.Promise
import play.api.libs.iteratee.Enumerator
import play.api.libs.iteratee.Iteratee
import play.api.mvc.Result
import scala.util.Random

class FileUploadIterateeSpec extends Specification {

  "FileUploadIteratee" should {

    /**
     * Implentation of FileUploadIteratee for testing, avoiding using the database
     */
    class TestIteratee extends FileUploadIteratee {

      // Upload implementation that store data in an attribute 
      case class TestUpload(userId: Long, guid: UUID, val bytesUploaded: Long, var data: Array[Byte] = Array[Byte]()) extends OverviewUpload {
        val lastActivity: Timestamp = new Timestamp(0)
        val contentsOid: Long = 1l

        def upload(chunk: Array[Byte]): TestUpload = {
          data = data ++ chunk
          withUploadedBytes(data.size)
        }

        def withUploadedBytes(bytesUploaded: Long): TestUpload = this.copy(bytesUploaded = bytesUploaded)

	def save: TestUpload = this
      }

      var currentUpload: TestUpload = _

      def createUpload(userId: Long, guid: UUID, filename: String, contentLength: Long): Option[OverviewUpload] = {
        currentUpload = TestUpload(userId, guid, 0l)
        Some(currentUpload)
      }

      def appendChunk(upload: OverviewUpload, chunk: Array[Byte]): Option[OverviewUpload] = {
        currentUpload = currentUpload.upload(chunk)
        Some(currentUpload)
      }

      def uploadedData: Array[Byte] = currentUpload.data
    }

    trait UploadContext extends Scope {
      val chunk = new Array[Byte](100)
      Random.nextBytes(chunk)
      
      val input = new ByteArrayInputStream(chunk)
      val userId = 1l
      val guid = UUID.randomUUID
      val uploadIteratee = new TestIteratee

      val enumerator: Enumerator[Array[Byte]]
      def upload: Option[OverviewUpload] = {
	val uploadPromise = for {
	  doneIt <- enumerator(uploadIteratee.store(userId, guid, "file", 100))
	  result: Option[OverviewUpload] <- doneIt.run
	} yield result
	uploadPromise.await.get
      }
      
    }

    trait SingleChunk extends UploadContext {
      val enumerator: Enumerator[Array[Byte]] = Enumerator.fromStream(input)
    }

    trait MultipleChunks extends UploadContext {
      val enumerator: Enumerator[Array[Byte]] = Enumerator.fromStream(input, 10)
    }



    "process Enumerator with one chunk only" in new SingleChunk {
      upload must beSome.like { case u => u.bytesUploaded must be equalTo (chunk.size) }
      uploadIteratee.uploadedData must be equalTo(chunk)
    }

    "process Enumerator with multiple chunks" in new MultipleChunks {
      upload must beSome.like { case u => u.bytesUploaded must be equalTo (chunk.size) }
      uploadIteratee.uploadedData must be equalTo(chunk)
    }
    
  }
}
