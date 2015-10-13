package com.overviewdocs.jobhandler.filegroup.task.step

import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import scala.concurrent.Future

import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.jobhandler.filegroup.task.PdfPage
import com.overviewdocs.models.Page
import com.overviewdocs.models.tables.Pages
import com.overviewdocs.test.DbSpecification

class PageSaverSpec extends DbSpecification with Mockito {
  "PageSaver" should {
    "save pages" in new DbScope {
      import database.api._

      val mockBlobStorage = smartMock[BlobStorage]
      mockBlobStorage.create(any, any, any) returns Future.successful("location")

      val pageSaver = new PageSaver {
        override protected val blobStorage = mockBlobStorage
      }

      val file = factory.file()

      val inData: Seq[(Array[Byte],String,Boolean)] = Seq(
        (Array[Byte](1, 2, 3), "page-1", true),
        (Array[Byte](2, 3, 4, 5), "page-2", false)
      )

      val result = for {
        attributes <- pageSaver.savePages(file.id, inData)
        outPages <- database.seq(Pages.filter(_.id inSet attributes.map(_.id)).map(_.createAttributes))
      } yield (attributes, outPages)

      val (attributes, outPages) = await(result)

      attributes.map(ra => (ra.fileId, ra.pageNumber, ra.text, ra.isFromOcr)) must beEqualTo(Seq(
        (file.id, 1, "page-1", true),
        (file.id, 2, "page-2", false)
      ))

      outPages must beEqualTo(Seq(
        Page.CreateAttributes(file.id, 1, "location", 3, "page-1", true),
        Page.CreateAttributes(file.id, 2, "location", 4, "page-2", false)
      ))
    }
  }
}
