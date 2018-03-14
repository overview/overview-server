package com.overviewdocs.blobstorage

import java.nio.file.Path
import scala.concurrent.Future

class BlobStorageStrategySpec extends StrategySpecification {
  trait BlankStrategy extends BlobStorageStrategy {
    override def get(location: String) = ???
    override def getUrl(location: String, mimeType: String) = ???
    override def getUrlOpt(location: String, mimeType: String) = ???
    override def delete(location: String): Future[Unit] = ???
    override def create(locationPrefix: String, dataPath: Path): Future[String] = ???
  }

  "#deleteMany" should {
    "fail immediately if any #delete fails immediately" in new BaseScope {
      val testException = new Throwable("boo")
      object TestStrategy extends BlankStrategy {
        override def delete(location: String): Future[Unit] = {
          if (location == "foo:bar") {
            Future.unit
          } else {
            throw testException // synchronous
          }
        }
      }
      TestStrategy.deleteMany(Seq("foo:bar", "bar:baz")) should throwA(testException)
    }

    "fail eventually if any #delete fails eventually" in new BaseScope {
      val testException = new Throwable("boo")
      object TestStrategy extends BlankStrategy {
        override def delete(location: String): Future[Unit] = {
          if (location == "foo:bar") {
            Future.unit
          } else {
            Future.failed(testException) // asynchronous
          }
        }
      }
      val future = TestStrategy.deleteMany(Seq("foo:bar", "bar:baz")) // no exception
      await(future) must throwA(testException)
    }

    "succeed if every #delete succeeds" in new BaseScope {
      object TestStrategy extends BlankStrategy {
        override def delete(location: String) = Future.unit
      }
      await(TestStrategy.deleteMany(Seq("foo:bar", "bar:baz"))) must beEqualTo(())
    }
  }
}
