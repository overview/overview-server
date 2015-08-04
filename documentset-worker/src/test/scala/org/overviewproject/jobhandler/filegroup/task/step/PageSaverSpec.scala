package com.overviewdocs.jobhandler.filegroup.task.step

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import scala.concurrent.Future

import com.overviewdocs.jobhandler.filegroup.task.PdfPage
import com.overviewdocs.models.Page
import com.overviewdocs.models.tables.Pages
import com.overviewdocs.test.DbSpecification

class PageSaverSpec extends DbSpecification with Mockito {

  "PageSaver" should {

    "save pages" in new PageContext {
      import database.api._

      val foundPages = for {
        attributes <- pageSaver.savePages(file.id, pageData.view)
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
    val pageData = Seq.tabulate(numberOfPages)(n => (Array[Byte](0, 1, 3), s"text-$n"))

    val expectedAttributes = Seq.tabulate(numberOfPages)(n => (file.id, n + 1, pageLocation, 3, s"text-$n"))

    val pageSaver = new TestPageSaver
  }

  protected class TestPageSaver extends PageSaver {
    override protected val pageBlobSaver = smartMock[PageBlobSaver]

    pageBlobSaver.save(any) returns Future.successful("page:location")

  }
}
