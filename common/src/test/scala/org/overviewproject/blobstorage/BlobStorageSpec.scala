package org.overviewproject.blobstorage

import java.io.{File,InputStream}
import java.nio.file.Files
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.iteratee.Enumerator
import scala.concurrent.{Await,Future}
import scala.concurrent.duration.Duration

class BlobStorageSpec extends Specification with Mockito {
  def await[T](future: Future[T]): T = Await.result(future, Duration.Inf)

  "BlobStorage" should {
    trait BaseScope extends Scope {
      val mockConfig = mock[BlobStorageConfig]
      val mockStrategyFactory = mock[StrategyFactory]
      object TestBlobStorage extends BlobStorage {
        override protected val config = mockConfig
        override protected val strategyFactory = mockStrategyFactory
      }
    }

    "#get" should {
      "resolve a BlobStorageStrategy from the location" in new BaseScope {
        mockStrategyFactory.forLocation(anyString) returns mock[BlobStorageStrategy]
        TestBlobStorage.get("s3:foo:bar")
        there was one(mockStrategyFactory).forLocation("s3:foo:bar")
      }

      "call BlobStorageStrategy#get" in new BaseScope {
        val mockStrategy = mock[BlobStorageStrategy]
        mockStrategyFactory.forLocation(anyString) returns mockStrategy
        TestBlobStorage.get("s3:foo:bar")
        there was one(mockStrategy).get("s3:foo:bar")
      }
    }

    "#withBlobInTempFile" should {
      trait WithBlobInTempFileScope extends BaseScope {
        val mockStrategy = mock[BlobStorageStrategy]
        mockStrategyFactory.forLocation("bucket:id") returns mockStrategy
        mockStrategy.get("bucket:id") returns Future.successful(Enumerator("blob".getBytes("utf-8")))
      }

      "supply a readable File to the callback" in new WithBlobInTempFileScope {
        val futureBytes = TestBlobStorage.withBlobInTempFile("bucket:id") { tempFile =>
          Future.successful(Files.readAllBytes(tempFile.toPath))
        }

        await(futureBytes) must beEqualTo("blob".getBytes("utf-8"))
      }

      "delete the File after the callback succeeds" in new WithBlobInTempFileScope {
        val futureFile = TestBlobStorage.withBlobInTempFile("bucket:id") { tempFile => Future.successful(tempFile) }
        await(futureFile).exists must beFalse
      }

      "throw an exception and delete the File after the callback throws an exception synchronously" in new WithBlobInTempFileScope {
        var file: Option[File] = None
        val exception = new Exception("foo")
        val future: Future[Unit] = TestBlobStorage.withBlobInTempFile("bucket:id") { tempFile =>
          file = Some(tempFile)
          throw exception
          Future.successful(())
        }
        await(future) must throwA(exception)
        file.map(_.exists) must beSome(false)
      }

      "throw an exception and delete the File after the callback throws an exception asynchronously" in new WithBlobInTempFileScope {
        var file: Option[File] = None
        val exception = new Exception("foo")
        val future: Future[Unit] = TestBlobStorage.withBlobInTempFile("bucket:id") { tempFile =>
          file = Some(tempFile)
          Future.failed(exception)
        }
        await(future) must throwA(exception)
        file.map(_.exists) must beSome(false)
      }
    }

    "#delete" should {
      "resolve a BlobStorageStrategy from the location" in new BaseScope {
        mockStrategyFactory.forLocation(anyString) returns mock[BlobStorageStrategy]
        TestBlobStorage.delete("s3:foo:bar")
        there was one(mockStrategyFactory).forLocation("s3:foo:bar")
      }

      "call BlobStorageStrategy#get" in new BaseScope {
        val mockStrategy = mock[BlobStorageStrategy]
        mockStrategyFactory.forLocation(anyString) returns mockStrategy
        TestBlobStorage.delete("s3:foo:bar")
        there was one(mockStrategy).delete("s3:foo:bar")
      }
    }

    "#deleteMany" should {
      "call BlobStorageStrategy#deleteMany for each strategy" in new BaseScope {
        val mockStrategy1 = mock[BlobStorageStrategy]
        val mockStrategy2 = mock[BlobStorageStrategy]
        mockStrategy1.deleteMany(any[Seq[String]]) returns(Future.successful(()))
        mockStrategy2.deleteMany(any[Seq[String]]) returns(Future.successful(()))
        mockStrategyFactory.forLocation("strat1:foo") returns mockStrategy1
        mockStrategyFactory.forLocation("strat2:foo") returns mockStrategy2
        mockStrategyFactory.forLocation("strat2:bar") returns mockStrategy2
        val result = await(TestBlobStorage.deleteMany(Seq("strat1:foo", "strat2:foo", "strat2:bar")))
        there was one(mockStrategy1).deleteMany(Seq("strat1:foo"))
        there was one(mockStrategy2).deleteMany(Seq("strat2:foo", "strat2:bar"))
        result must beEqualTo(())
      }

      "fail when any BlobStorageStrategy#deleteMany fails" in new BaseScope {
        val sillyException = new Throwable("not good")
        val mockStrategy1 = mock[BlobStorageStrategy]
        val mockStrategy2 = mock[BlobStorageStrategy]
        mockStrategy1.deleteMany(any[Seq[String]]) returns(Future.failed(sillyException))
        mockStrategy2.deleteMany(any[Seq[String]]) returns(Future.successful(()))
        mockStrategyFactory.forLocation("strat1:foo") returns mockStrategy1
        mockStrategyFactory.forLocation("strat2:foo") returns mockStrategy2
        mockStrategyFactory.forLocation("strat2:bar") returns mockStrategy2
        await(TestBlobStorage.deleteMany(Seq("strat1:foo", "strat2:foo", "strat2:bar"))) must throwA(sillyException)
        there was one(mockStrategy1).deleteMany(Seq("strat1:foo"))
        there was one(mockStrategy2).deleteMany(Seq("strat2:foo", "strat2:bar"))
      }
    }

    "#create" should {
      "find the location prefix from config" in new BaseScope {
        mockConfig.getPreferredPrefix(any[BlobBucketId]) returns "s3:foo"
        mockStrategyFactory.forLocation(anyString) returns mock[BlobStorageStrategy]
        TestBlobStorage.create(BlobBucketId.PageData, mock[InputStream], 100)
        there was one(mockConfig).getPreferredPrefix(BlobBucketId.PageData)
      }

      "find the strategy from the location prefix" in new BaseScope {
        mockConfig.getPreferredPrefix(any[BlobBucketId]) returns "s3:foo"
        mockStrategyFactory.forLocation(anyString) returns mock[BlobStorageStrategy]
        TestBlobStorage.create(BlobBucketId.PageData, mock[InputStream], 100)
        there was one(mockStrategyFactory).forLocation("s3:foo")
      }

      "call create() with the location prefix and strategy" in new BaseScope {
        val mockStrategy = mock[BlobStorageStrategy]
        val mockInputStream = mock[InputStream]
        mockConfig.getPreferredPrefix(any[BlobBucketId]) returns "s3:foo"
        mockStrategyFactory.forLocation(anyString) returns mockStrategy
        TestBlobStorage.create(BlobBucketId.PageData, mockInputStream, 100)
        there was one(mockStrategy).create("s3:foo", mockInputStream, 100)
      }
    }
  }
}
