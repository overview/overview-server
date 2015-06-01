package org.overviewproject.jobhandler.filegroup.task.step

import org.specs2.mutable.Specification
import org.overviewproject.test.SlickSpecification
import org.overviewproject.jobhandler.filegroup.task.PdfPage
import org.specs2.mock.Mockito
import scala.concurrent.Future
import org.overviewproject.models.Page
import org.overviewproject.models.tables.Pages
import org.overviewproject.test.SlickClientInSession
import slick.jdbc.JdbcBackend.Session

class PageSaverSpec extends SlickSpecification with Mockito {

  "PageSaver" should {

    "save pages" in new PageContext {
      import org.overviewproject.database.Slick.simple._

      val foundPages = for {
        attributes <- pageSaver.savePages(file.id, pages.view)
        ids = attributes.map(_.id)
      } yield Pages.filter(_.id inSet ids).list

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

  protected class TestPageSaver(implicit val session: Session) extends PageSaver with SlickClientInSession {
    override protected val pageBlobSaver = smartMock[PageBlobSaver]

    pageBlobSaver.save(any) returns Future.successful("page:location")

  }
}
