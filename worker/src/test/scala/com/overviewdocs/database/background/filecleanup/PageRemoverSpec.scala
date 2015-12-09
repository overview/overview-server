package com.overviewdocs.background.filecleanup

import scala.concurrent.{ Await, Future, Promise, TimeoutException }
import scala.concurrent.duration._
import org.specs2.mock.Mockito

import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.models.tables.Pages
import com.overviewdocs.test.DbSpecification

class PageRemoverSpec extends DbSpecification with Mockito {
  "PageRemover" should {
    trait BaseScope extends DbScope {
      val file = factory.file(referenceCount = 0)
      factory.page(fileId=file.id, pageNumber=1, dataLocation="test:1")
      factory.page(fileId=file.id, pageNumber=2, dataLocation="test:2")

      val mockBlobStorage = smartMock[BlobStorage]

      val subject = new PageRemover {
        override protected val blobStorage = mockBlobStorage
      }
    }

    "delete blobs" in new BaseScope {
      mockBlobStorage.deleteMany(any) returns Future.successful(())
      await(subject.removeFilePages(file.id))

      there was one(mockBlobStorage).deleteMany(argThat(beLike[Seq[String]] { case actual: Seq[String] =>
        actual must containTheSameElementsAs(Seq("test:1", "test:2"))
      }))
    }

    "delete blobs first, then their pages" in new BaseScope {
      val blobsDeleted = Promise[Unit]()
      mockBlobStorage.deleteMany(any) returns blobsDeleted.future
      val done = subject.removeFilePages(file.id)

      blockingDatabase.length(Pages) must beEqualTo(2)
      blobsDeleted.success(())
      await(done)
      blockingDatabase.length(Pages) must beEqualTo(0)
    }
  }
}
