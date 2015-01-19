package org.overviewproject.background.filecleanup

import scala.concurrent.Future
import org.specs2.mock.Mockito
import org.overviewproject.blobstorage.BlobStorage
import org.overviewproject.test.{ SlickClientInSession, SlickSpecification }
import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.tables.Pages

class PageRemoverSpec extends SlickSpecification with Mockito {

  "PageRemover" should {

    "delete blobs" in new PageScope {
      await(remover.deleteFilePages(file.id))
      
      val locations = pages.map(_.dataLocation) 
      there was one(mockBlobStorage).deleteMany(locations)
    }

    "delete pages" in new PageScope {
      await(remover.deleteFilePages(file.id))
      
      Pages.filter(_.fileId === file.id).firstOption must beNone
    }
  }

  trait PageScope extends DbScope {
    val numberOfPages = 10
    val file = factory.file(referenceCount = 0)
    val pages = Seq.tabulate(numberOfPages)(n =>
      factory.page(fileId = file.id, pageNumber = n + 1, dataLocation = s"test:$n"))

    val mockBlobStorage = smartMock[BlobStorage]
    mockBlobStorage.delete(any) returns Future.successful(())
    
    val remover = new TestPageRemover(mockBlobStorage)
  }

  class TestPageRemover(bs: BlobStorage)(implicit val session: Session) extends PageRemover with SlickClientInSession {
    override protected val blobStorage = bs
  }
}