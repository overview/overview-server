package com.overviewdocs.blobstorage

import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.util.ByteString
import java.io.File
import java.nio.file.{Files,Path}
import org.specs2.mock.Mockito
import org.specs2.mutable.{After,Specification}
import org.specs2.specification.Scope
import scala.concurrent.{Await,Future}
import scala.concurrent.duration.Duration
import com.overviewdocs.util.Logger

class BlobStorageSpec extends Specification with Mockito {
  def await[T](future: Future[T]): T = Await.result(future, Duration.Inf)

  "BlobStorage" should {
    trait BaseScope extends Scope {
      val mockConfig = mock[BlobStorageConfig]
      val mockStrategyFactory = mock[StrategyFactory]
      val mockLogger = mock[Logger]
      object TestBlobStorage extends BlobStorage {
        override protected val config = mockConfig
        override protected val strategyFactory = mockStrategyFactory
        override protected val logger = mockLogger
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

    "#getUrl" should {
      "resolve a BlobStorageStrategy from the location" in new BaseScope {
        mockStrategyFactory.forLocation(anyString) returns mock[BlobStorageStrategy]
        TestBlobStorage.getUrl("s3:foo:bar", "image/png")
        there was one(mockStrategyFactory).forLocation("s3:foo:bar")
      }

      "call BlobStorageStrategy#getUrl" in new BaseScope {
        val mockStrategy = mock[BlobStorageStrategy]
        mockStrategyFactory.forLocation(anyString) returns mockStrategy
        mockStrategy.getUrl("s3:foo:bar", "image/png") returns Future.successful("data:foo")
        await(TestBlobStorage.getUrl("s3:foo:bar", "image/png")) must beEqualTo("data:foo")
      }
    }

    "#withBlobInTempFile" should {
      trait WithBlobInTempFileScope extends BaseScope with After {
        implicit val system = ActorSystem("WithBlobInTempFileScope")
        val mockStrategy = mock[BlobStorageStrategy]
        mockStrategyFactory.forLocation("bucket:id") returns mockStrategy
        mockStrategy.get("bucket:id") returns Source.single(ByteString("blob".getBytes("utf-8")))

        override def after = system.terminate
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
          Future.unit
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
        mockStrategy1.deleteMany(any[Seq[String]]) returns(Future.unit)
        mockStrategy2.deleteMany(any[Seq[String]]) returns(Future.unit)
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
        mockStrategy2.deleteMany(any[Seq[String]]) returns(Future.unit)
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
        TestBlobStorage.create(BlobBucketId.PageData, mock[Path])
        there was one(mockConfig).getPreferredPrefix(BlobBucketId.PageData)
      }

      "find the strategy from the location prefix" in new BaseScope {
        mockConfig.getPreferredPrefix(any[BlobBucketId]) returns "s3:foo"
        mockStrategyFactory.forLocation(anyString) returns mock[BlobStorageStrategy]
        TestBlobStorage.create(BlobBucketId.PageData, mock[Path])
        there was one(mockStrategyFactory).forLocation("s3:foo")
      }

      "call create() with the location prefix and strategy" in new BaseScope {
        val mockStrategy = mock[BlobStorageStrategy]
        val mockFile = mock[Path]
        mockConfig.getPreferredPrefix(any[BlobBucketId]) returns "s3:foo"
        mockStrategyFactory.forLocation(anyString) returns mockStrategy
        TestBlobStorage.create(BlobBucketId.PageData, mockFile)
        there was one(mockStrategy).create("s3:foo", mockFile)
      }
    }
  }
}
