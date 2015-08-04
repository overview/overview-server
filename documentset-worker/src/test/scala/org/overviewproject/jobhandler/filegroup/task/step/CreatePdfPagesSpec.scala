package com.overviewdocs.jobhandler.filegroup.task.step

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import com.overviewdocs.jobhandler.filegroup.task.PdfPage
import com.overviewdocs.models.File
import com.overviewdocs.jobhandler.filegroup.task.PdfDocument
import scala.concurrent.Future
import com.overviewdocs.models.Page
import scala.concurrent.ExecutionContext

class CreatePdfPagesSpec extends Specification with Mockito {

  "CreatePdfPages" should {

    "write pages to blob storage" in new FileScope {
      Await.result(createPdfPages.execute, Duration.Inf)

      there was one(pageSaver).savePages(fileId, savedPageData.view)
    }

    "generate next step with page data" in new FileScope {
      val r = createPdfPages.execute.map {
        case NextStep(d) => d
      }

      r must be_==(pageData).await
    }
  }

  case class NextStep(pages: Iterable[DocumentData]) extends TaskStep {
    override def execute = Future.successful(this)
  }

  trait FileScope extends Scope {
    val documentSetId: Long = 10l
    val fileId: Long = 1l
    val fileName: String = "file name"
    val viewLocation: String = "view:location"

    val file = smartMock[File]
    file.id returns fileId
    file.name returns fileName
    file.viewLocation returns viewLocation

    val pageText = "page text"
    val pageBytes = Array[Byte](1, 2, 4)

    val pages = Seq.fill(3)(smartMock[PdfPage])
    pages.foreach { p =>
      p.text returns pageText
      p.data returns pageBytes
    }
    
    val savedPageData = Seq.fill(3)((pageBytes, pageText))
    
    val pageAttributes = Seq.tabulate(pages.length)(n => Page.ReferenceAttributes(n, fileId, n, pageText))

    val pageData = pageAttributes.map { p => PdfPageDocumentData(fileName, fileId, p.pageNumber, p.id, pageText) }

    val createPdfPages = new TestCreatePdfPages(documentSetId, file)

    def pageSaver = createPdfPages.mockPageSaver

    class TestCreatePdfPages(
      override protected val documentSetId: Long,
      override protected val file: File)
      extends CreatePdfPages {

      override protected val executor: ExecutionContext = implicitly

      override protected val pdfProcessor = smartMock[PdfProcessor]
      override protected val pageSaver = smartMock[PageSaver]
      override protected val nextStep = { pageData => NextStep(pageData) }
      private val pdfDocument = smartMock[PdfDocument]

      pdfProcessor.loadFromBlobStorage(viewLocation) returns Future.successful(pdfDocument)
      pdfDocument.pages returns pages.view

      pageSaver.savePages(be_===(fileId), any) returns Future.successful(pageAttributes)

      def mockPageSaver = pageSaver
    }

  }

}