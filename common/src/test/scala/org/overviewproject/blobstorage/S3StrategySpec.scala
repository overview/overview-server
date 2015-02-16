package org.overviewproject.blobstorage

import com.amazonaws.AmazonClientException
import com.amazonaws.services.s3.AmazonS3
import com.amazonaws.services.s3.model.{AmazonS3Exception,DeleteObjectsRequest,ObjectMetadata}
import com.amazonaws.services.s3.transfer.{Download,TransferManager,Upload}
import com.amazonaws.event.{ProgressEvent,ProgressEventType,ProgressListener}
import java.io.{File,InputStream}
import java.nio.file.Files
import org.specs2.matcher.{Expectable,Matcher}

class S3StrategySpec extends StrategySpecification {
  class DeleteManyRequestLike(bucket: String, keys: Seq[String]) extends Matcher[DeleteObjectsRequest] {
    def apply[S <: DeleteObjectsRequest](req: Expectable[S]) = {
      import scala.collection.JavaConverters.asScalaBufferConverter
      result(
        bucket == req.value.getBucketName && keys == req.value.getKeys.asScala.toSeq.map(_.getKey),
        s"${req.description} is the correct request",
        s"${req.description} has the wrong bucket (should be ${bucket}) or the wrong keys (should be ${keys.toString})",
        req
      )
    }
  }
  def deleteManyRequestLike(bucket: String, keys: Seq[String]) = new DeleteManyRequestLike(bucket, keys)

  class MockTransferManager extends TransferManager {
    case class LastDownload(bucket: String, key: String, file: File)
    case class LastUpload(bucket: String, key: String, inputStream: InputStream, metadata: ObjectMetadata)

    var lastDownload: Option[LastDownload] = None
    var lastUpload: Option[LastUpload] = None
    var lastProgressListener: Option[ProgressListener] = None
    var lastException: Option[AmazonClientException] = None

    def succeed = {
      lastProgressListener.foreach { pl =>
        pl.progressChanged(new ProgressEvent(ProgressEventType.TRANSFER_COMPLETED_EVENT))
      }
    }

    def fail(ex: AmazonClientException) = {
      lastException = Some(ex)
      lastProgressListener.foreach { pl =>
        pl.progressChanged(new ProgressEvent(ProgressEventType.TRANSFER_FAILED_EVENT))
      }
    }

    override def download(bucket: String, key: String, file: File) = {
      lastDownload = Some(LastDownload(bucket, key, file))
      val ret = mock[Download]
      ret.addProgressListener(any[ProgressListener]) answers { (pl) => lastProgressListener = Some(pl.asInstanceOf[ProgressListener]) }
      ret.waitForException answers { (_) => lastException.get }
      ret.waitForCompletion answers { (_) => if (!lastException.isEmpty) throw lastException.get }
      ret
    }

    override def upload(bucket: String, key: String, inputStream: InputStream, metadata: ObjectMetadata) = {
      lastUpload = Some(LastUpload(bucket, key, inputStream, metadata))
      val ret = mock[Upload]
      ret.addProgressListener(any[ProgressListener]) answers { (pl) => lastProgressListener = Some(pl.asInstanceOf[ProgressListener]) }
      ret.waitForException answers { (_) => lastException.get }
      ret.waitForCompletion answers { (_) => if (!lastException.isEmpty) throw lastException.get }
      ret
    }
  }

  trait S3BaseScope extends BaseScope {
    val mockS3 = mock[AmazonS3]
    val mockTransferManager = new MockTransferManager
    object TestStrategy extends S3Strategy {
      override val s3 = mockS3
      override val transferManager = mockTransferManager
    }
  }

  "#get" should {
    "fail when the location is invalid" in new S3BaseScope {
      TestStrategy.get("foo:bar") must throwA[IllegalArgumentException]
      TestStrategy.get("s3::bar") must throwA[IllegalArgumentException]
      TestStrategy.get("s3:foo:") must throwA[IllegalArgumentException]
      TestStrategy.get("s3:foo:bar:baz") must throwA[IllegalArgumentException]
    }

    "provide contents when the tempfile is full" in new S3BaseScope {
      val futureEnumerator = TestStrategy.get("s3:foo:bar")
      Files.write(mockTransferManager.lastDownload.get.file.toPath, "file contents".getBytes("utf-8"))
      mockTransferManager.succeed
      val enumerator = await(futureEnumerator)
      consume(enumerator) must beEqualTo("file contents".getBytes("utf-8"))
    }

    "delete the file after enumerating" in new S3BaseScope {
      val futureEnumerator = TestStrategy.get("s3:foo:bar")
      mockTransferManager.succeed
      val enumerator = await(futureEnumerator)
      consume(enumerator)
      mockTransferManager.lastDownload.get.file.exists must beFalse
    }

    "delete the file before enumerator is consumed, in case enumeration fails" in new S3BaseScope {
      // This won't work on Windows. It _will_ work with Docker.
      val futureEnumerator = TestStrategy.get("s3:foo:bar")
      mockTransferManager.succeed
      val enumerator = await(futureEnumerator)
      mockTransferManager.lastDownload.get.file.exists must beFalse
    }

    "fail when the transfer fails" in new S3BaseScope {
      val ex = new AmazonS3Exception("boo")
      val futureEnumerator = TestStrategy.get("s3:foo:bar")
      mockTransferManager.fail(ex)
      await(futureEnumerator) must throwA(ex)
    }

    "delete the file when the transfer fails" in new S3BaseScope {
      val ex = new AmazonS3Exception("boo")
      val futureEnumerator = TestStrategy.get("s3:foo:bar")
      mockTransferManager.fail(ex)
      await(futureEnumerator) must throwA[Exception] // "must throwA" to ignore the exception
      mockTransferManager.lastDownload.get.file.exists must beFalse
    }
  }

  "#delete" should {
    "fail when the location is invalid" in new S3BaseScope {
      TestStrategy.delete("foo:bar") must throwA[IllegalArgumentException]
      TestStrategy.delete("s3::bar") must throwA[IllegalArgumentException]
      TestStrategy.delete("s3:foo:") must throwA[IllegalArgumentException]
      TestStrategy.delete("s3:foo:bar:baz") must throwA[IllegalArgumentException]
    }

    "succeed on success" in new S3BaseScope {
      await(TestStrategy.delete("s3:foo:bar")) must beEqualTo(())
      there was one(mockS3).deleteObjects(deleteManyRequestLike("foo", Seq("bar")))
    }

    "succeed when the bucket does not exist" in new S3BaseScope {
      // S3 returns a 404 when the bucket does not exist
      val ex = new AmazonS3Exception("boo")
      ex.setStatusCode(404)
      mockS3.deleteObjects(any) throws ex
      await(TestStrategy.delete("s3:foo:bar")) must beEqualTo(())
    }

    "succeed when the object does not exist" in new S3BaseScope {
      import com.amazonaws.services.s3.model.MultiObjectDeleteException
      import java.util.Collections

      val deleteError = new MultiObjectDeleteException.DeleteError()
      deleteError.setCode("NoSuchKey")
      val ex = new MultiObjectDeleteException(Collections.singletonList(deleteError), Collections.emptyList())

      mockS3.deleteObjects(any[DeleteObjectsRequest]) throws ex

      await(TestStrategy.delete("s3:foo:bar")) must not(throwA[Exception])
    }
  }

  "#deleteMany" should {
    "fail when one location is invalid" in new S3BaseScope {
      TestStrategy.deleteMany(Seq("s3:foo:bar", "foo:bar")) must throwA[IllegalArgumentException]
      TestStrategy.deleteMany(Seq("s3:foo:bar", "s3::bar")) must throwA[IllegalArgumentException]
      TestStrategy.deleteMany(Seq("s3:foo:bar", "s3:foo:")) must throwA[IllegalArgumentException]
      TestStrategy.deleteMany(Seq("s3:foo:bar", "s3:foo:bar:baz")) must throwA[IllegalArgumentException]
    }

    "succeed on success" in new S3BaseScope {
      await(TestStrategy.deleteMany(Seq("s3:foo:bar", "s3:foo:baz"))) must beEqualTo(())
      there was one(mockS3).deleteObjects(deleteManyRequestLike("foo", Seq("bar", "baz")))
    }

    "succeed when deleting from multiple buckets" in new S3BaseScope {
      await(TestStrategy.deleteMany(Seq("s3:foo:bar", "s3:bar:baz"))) must beEqualTo(())
      there was one(mockS3).deleteObjects(deleteManyRequestLike("foo", Seq("bar")))
      there was one(mockS3).deleteObjects(deleteManyRequestLike("bar", Seq("baz")))
    }

    "batch requests by 1,000" in new S3BaseScope {
      // 1,000 is the Amazon-documented limit
      val keys1 = Range(0, 1000).toSeq.map(_.toString)
      val keys2 = Range(1000, 1002).toSeq.map(_.toString)

      val locations = (keys1 ++ keys2).map((s) => "s3:foo:" + s)

      await(TestStrategy.deleteMany(locations)) must beEqualTo(())
      there was one(mockS3).deleteObjects(deleteManyRequestLike("foo", keys1))
      there was one(mockS3).deleteObjects(deleteManyRequestLike("foo", keys2))
    }

    "succeed when a bucket does not exist" in new S3BaseScope {
      // S3 returns a 404 when the bucket does not exist
      val ex = new AmazonS3Exception("boo")
      ex.setStatusCode(404)
      mockS3.deleteObjects(any[DeleteObjectsRequest]) throws ex
      await(TestStrategy.deleteMany(Seq("s3:foo:bar", "s3:foo:baz"))) must beEqualTo(())
    }

    "succeed when an object does not exist" in new S3BaseScope {
      import com.amazonaws.services.s3.model.MultiObjectDeleteException
      import java.util.Collections

      val deleteError = new MultiObjectDeleteException.DeleteError()
      deleteError.setCode("NoSuchKey")
      val ex = new MultiObjectDeleteException(Collections.singletonList(deleteError), Collections.emptyList())

      mockS3.deleteObjects(any[DeleteObjectsRequest]) throws ex

      await(TestStrategy.deleteMany(Seq("s3:foo:bar", "s3:foo:baz"))) must not(throwA[Exception])
    }

    "fail on AccessDenied" in new S3BaseScope {
      // Mostly, we're testing that our succeed-when-not-exists logic isn't
      // simply succeed-on-error.
      import com.amazonaws.services.s3.model.MultiObjectDeleteException
      import java.util.Collections

      val deleteError = new MultiObjectDeleteException.DeleteError()
      deleteError.setCode("AccessDenied")
      val ex = new MultiObjectDeleteException(Collections.singletonList(deleteError), Collections.emptyList())

      mockS3.deleteObjects(any[DeleteObjectsRequest]) throws ex

      await(TestStrategy.deleteMany(Seq("s3:foo:bar", "s3:foo:baz"))) must throwA[Exception]
    }

    "succeed without sending a request when there are no locations" in new S3BaseScope {
      await(TestStrategy.deleteMany(Seq[String]())) must beEqualTo(())
      there was no(mockS3).deleteObjects(any)
    }
  }

  "#create" should {
    "fail when the location prefix is invalid" in new S3BaseScope {
      TestStrategy.create("foo:bar", utf8InputStream("content 1"), 9) must throwA[IllegalArgumentException]
      TestStrategy.create("s3:foo:", utf8InputStream("content 1"), 9) must throwA[IllegalArgumentException]
      TestStrategy.create("s3:", utf8InputStream("content 1"), 9) must throwA[IllegalArgumentException]
    }

    "create random UUID filenames" in new S3BaseScope {
      val LocationRegex = "^s3:foo:[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$".r
      val name1Future = TestStrategy.create("s3:foo", utf8InputStream("content 1"), 9)
      mockTransferManager.succeed
      val name2Future = TestStrategy.create("s3:foo", utf8InputStream("content 1"), 9)
      mockTransferManager.succeed
      await(name1Future) must beMatching(LocationRegex)
      await(name2Future) must beMatching(LocationRegex)
      await(name1Future) must not(beEqualTo(await(name2Future)))
    }

    "send the uploads to S3" in new S3BaseScope {
      val UuidRegex = "^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$".r

      val inputStream = utf8InputStream("content 1")
      val future = TestStrategy.create("s3:foo", inputStream, 9)
      mockTransferManager.lastUpload must beSome
      mockTransferManager.lastUpload.get.bucket must beEqualTo("foo")
      mockTransferManager.lastUpload.get.key must beMatching(UuidRegex)
      mockTransferManager.lastUpload.get.inputStream must beEqualTo(inputStream)
      mockTransferManager.lastUpload.get.metadata.getContentLength must beEqualTo(9)
    }

    "succeed when the upload succeeds" in new S3BaseScope {
      val LocationRegex = "^s3:foo:[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$".r
      val future = TestStrategy.create("s3:foo", utf8InputStream("content"), 7)
      mockTransferManager.succeed
      await(future) must beMatching(LocationRegex)
    }

    "fail when the upload fails" in new S3BaseScope {
      val ex = new AmazonS3Exception("boo")
      val future = TestStrategy.create("s3:foo", utf8InputStream("content"), 7)
      mockTransferManager.fail(ex)
      await(future) must throwA(ex)
    }
  }
}
