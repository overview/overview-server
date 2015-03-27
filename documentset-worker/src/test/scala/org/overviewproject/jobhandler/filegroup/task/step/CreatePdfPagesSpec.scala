package org.overviewproject.jobhandler.filegroup.task.step

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import org.overviewproject.jobhandler.filegroup.task.PdfPage
import org.overviewproject.models.File
import org.overviewproject.jobhandler.filegroup.task.PdfDocument
import scala.concurrent.Future

class CreatePdfPagesSpec extends Specification with Mockito {

  "CreatePdfPages" should {

    "write pages to blob storage" in new FileScope {
      Await.result(createPdfPages.execute, Duration.Inf)

      there was one(storage).savePages(fileId, pages)
    }

    "generate next step with page data" in new FileScope {
      val r = createPdfPages.execute.map {
        case NextStep(d) => d
      }

      r must be_==(pageData).await
    }
  }

  case class NextStep(pages: Iterable[PdfPageDocumentData]) extends TaskStep {
    override def execute = Future.successful(this)
  }

  trait FileScope extends Scope {
    val fileId: Long = 1l
    val fileName: String = "file name"
    
    val file = smartMock[File]
    file.id returns fileId
    file.name returns fileName
    
    val pageText = "page text"
    val pages = Seq.fill(3)(smartMock[PdfPage])
    pages.foreach { _.text returns pageText }
    
    val pageIds = Seq.tabulate(pages.length)(_ * 10l)
    val pageData = pageIds.zipWithIndex.map { p => PdfPageDocumentData(fileName, fileId, p._2, p._1, pageText) }

    val createPdfPages = new TestCreatePdfPages(file, pages, pageIds)

    def storage = createPdfPages.mockStorage
  }

  class TestCreatePdfPages(override protected val file: File, pages: Iterable[PdfPage], pageIds: Seq[Long])
    extends CreatePdfPages {
    override protected val pdfProcessor = smartMock[PdfProcessor]
    override protected val storage = smartMock[Storage]
    override protected val nextStep = { pageData: Iterable[PdfPageDocumentData] => NextStep(pageData) }
    private val pdfDocument = smartMock[PdfDocument]

    pdfProcessor.loadFromBlobStorage(any) returns pdfDocument
    pdfDocument.pages returns pages

    storage.savePages(any, any) returns pageIds
    def mockStorage = storage
  }
}