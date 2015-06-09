package org.overviewproject.jobhandler.filegroup.task.step

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import scala.concurrent.Future

import org.overviewproject.jobhandler.filegroup.task.PdfPage
import org.overviewproject.models.Page
import org.overviewproject.models.tables.Pages
import org.overviewproject.test.DbSpecification

class PageSaverSpec extends DbSpecification with Mockito {

  "PageSaver" should {

    "save pages" in new PageContext {
      import databaseApi._

      val foundPages = for {
        attributes <- pageSaver.savePages(file.id, pages.view)
        ids = attributes.map(_.id)
      } yield blockingDatabase.seq(Pages.filter(_.id inSet ids))

      val attributes = await(foundPages).map(p =>
          (p.fileId, p.pageNumber, p.dataLocation, p.dataSize, p.text.getOrElse("")))

      attributes must containTheSameElementsAs(expectedAttributes)
    }
  }

  trait PageContext extends DbScope {
    val pageLocation = "page:location"

    val file = factory.file()
    val numberOfPages = 10
    val pages = Seq.tabulate(numberOfPages)(n => PdfPage(Array[Byte](0, 1, 3), s"text-$n"))

    val expectedAttributes = Seq.tabulate(numberOfPages)(n => (file.id, n + 1, pageLocation, 3, s"text-$n"))

    val pageSaver = new TestPageSaver
  }

  protected class TestPageSaver extends PageSaver with org.overviewproject.database.DatabaseProvider {
    override protected val pageBlobSaver = smartMock[PageBlobSaver]

    pageBlobSaver.save(any) returns Future.successful("page:location")

  }
}
