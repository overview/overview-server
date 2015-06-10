package org.overviewproject.background.filecleanup

import scala.concurrent.{ Await, Future, Promise, TimeoutException }
import scala.concurrent.duration._
import org.specs2.mock.Mockito
import org.specs2.time.NoTimeConversions

import org.overviewproject.blobstorage.BlobStorage
import org.overviewproject.models.tables.Pages
import org.overviewproject.test.DbSpecification

class PageRemoverSpec extends DbSpecification with Mockito with NoTimeConversions {

  "PageRemover" should {

    "delete blobs" in new PageScope {
      blobsDeleted.success(())
      await(remover.removeFilePages(file.id))

      val locations = pages.map(_.dataLocation)
      there was one(mockBlobStorage).deleteMany(argThat(beLike[Seq[String]] { case actual: Seq[String] =>
        actual must containTheSameElementsAs(locations)
      }))
    }

    "delete pages after blobs are deleted" in new PageScope {
      val r = remover.removeFilePages(file.id)

      Await.result(r, 10 millis) must throwA[TimeoutException]

      import database.api._

      blockingDatabase.length(Pages.filter(_.fileId === file.id)) must beEqualTo(numberOfPages)

      blobsDeleted.success(())
      await(r)

      blockingDatabase.length(Pages.filter(_.fileId === file.id)) must beEqualTo(0)
    }
  }

  trait PageScope extends DbScope {
    val numberOfPages = 3
    val file = factory.file(referenceCount = 0)
    val pages = Seq.tabulate(numberOfPages)(n =>
      factory.page(fileId = file.id, pageNumber = n + 1, dataLocation = s"test:$n"))

    val mockBlobStorage = smartMock[BlobStorage]
    val blobsDeleted = Promise[Unit]()

    mockBlobStorage.deleteMany(any) returns blobsDeleted.future

    val remover = new TestPageRemover(mockBlobStorage)
  }

  class TestPageRemover(bs: BlobStorage) extends PageRemover {
    override protected val blobStorage = bs
  }
}
