package org.overviewproject.background.filecleanup

import scala.concurrent.{ Await, Future, Promise, TimeoutException }
import scala.concurrent.duration._
import org.specs2.mock.Mockito
import org.specs2.time.NoTimeConversions
import org.overviewproject.blobstorage.BlobStorage
import org.overviewproject.test.{ SlickClientInSession, SlickSpecification }
import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.tables.Pages

class PageRemoverSpec extends SlickSpecification with Mockito with NoTimeConversions {

  "PageRemover" should {

    "delete blobs" in new PageScope {
      blobsDeleted.success(())
      await(remover.removeFilePages(file.id))
      
      val locations = pages.map(_.dataLocation) 
      there was one(mockBlobStorage).deleteMany(locations)
    }

    "delete pages after blobs are deleted" in new PageScope {
      val r = remover.removeFilePages(file.id)
      
      Await.result(r, 10 millis) must throwA[TimeoutException]
      
      Pages.filter(_.fileId === file.id).firstOption must beSome
      
      blobsDeleted.success(())
      await(r)
      
      Pages.filter(_.fileId === file.id).firstOption must beNone
    }
  }

  trait PageScope extends DbScope {
    val numberOfPages = 10
    val file = factory.file(referenceCount = 0)
    val pages = Seq.tabulate(numberOfPages)(n =>
      factory.page(fileId = file.id, pageNumber = n + 1, dataLocation = s"test:$n"))

    val mockBlobStorage = smartMock[BlobStorage]
    val blobsDeleted = Promise[Unit]()
    
    mockBlobStorage.deleteMany(any) returns blobsDeleted.future
    
    val remover = new TestPageRemover(mockBlobStorage)
  }

  class TestPageRemover(bs: BlobStorage)(implicit val session: Session) extends PageRemover with SlickClientInSession {
    override protected val blobStorage = bs
  }
}