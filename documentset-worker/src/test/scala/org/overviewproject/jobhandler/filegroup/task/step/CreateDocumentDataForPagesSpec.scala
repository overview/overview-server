package org.overviewproject.jobhandler.filegroup.task.step

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.mock.Mockito
import org.overviewproject.models.File
import org.overviewproject.jobhandler.filegroup.task.PdfDocument
import scala.concurrent.Future
import org.overviewproject.jobhandler.filegroup.task.PdfPage
import org.overviewproject.models.Page

class CreateDocumentDataForPagesSpec extends Specification with Mockito {

  "CreateDocumentDataForPages" should {
    
    "create DocumentData for each text page" in new TextContext {
      val result = createDocumentDataForPages.execute
      
      result must be_==(NextStep(Seq(pageData))).await
    }
  }
  
  case class NextStep(documentData: Seq[DocumentData]) extends TaskStep {
    override def execute = Future.successful(this)
  }
  
  trait TextContext extends Scope {
    val fileId = 10l
    val filename = "file name"

    val text = "page text"
    val data = Array[Byte](1, 2, 3)
    val pageId = 20l
    
    val mockFile = smartMock[File]
    val mockPdfDocument = smartMock[PdfDocument]
    val mockPageSaver = smartMock[PageSaver]
    val mockPage = smartMock[PdfPage]    
    val mockPageAttributes = smartMock[Page.ReferenceAttributes]
    
    mockFile.id returns fileId
    mockFile.name returns filename
    
    mockPage.data returns data
    
    mockPdfDocument.pages returns Seq(mockPage).view
    
    mockPageSaver.savePages(fileId, Seq((data, text)).view) returns Future.successful(Seq(mockPageAttributes))
    
    mockPageAttributes.pageNumber returns 1
    mockPageAttributes.id returns pageId
    mockPageAttributes.text returns text
    
    val pageData = PdfPageDocumentData(filename, fileId, 1, pageId,  text)
    val createDocumentDataForPages = new TestCreateDocumentDataForPages
    
    class TestCreateDocumentDataForPages extends CreateDocumentDataForPages {
      override protected val executor = scala.concurrent.ExecutionContext.global
      override protected val documentSetId = 1l
      override protected val file = mockFile
      
      override protected val pdfDocument = mockPdfDocument
      override protected val textPages = Seq(text)
      override protected val pageSaver = mockPageSaver
      
      override protected val nextStep = NextStep
    }
  }
}