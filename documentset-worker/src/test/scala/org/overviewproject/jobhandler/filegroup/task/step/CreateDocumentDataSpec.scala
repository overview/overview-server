package org.overviewproject.jobhandler.filegroup.task.step

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.mock.Mockito
import org.overviewproject.models.File
import org.overviewproject.jobhandler.filegroup.task.PdfDocument
import scala.concurrent.Future
import scala.concurrent.Await
import scala.concurrent.duration.Duration


class CreateDocumentDataSpec extends Specification with Mockito {

  "CreateDocumentData" should {
    
    "create DocumentData from text" in new TextContext {
      val combinedText = texts.mkString("")
      
      val result = createDocumentData.execute
      
      result must be_==(NextStep(Seq(PdfFileDocumentData(filename, fileId, combinedText)))).await
    }
    
    "close pdfDocument" in new TextContext { 
      Await.ready(createDocumentData.execute, Duration.Inf)
      
      there was one(mockPdfDocument).close
    }
  }
  
  case class NextStep(documentData: Seq[DocumentData]) extends TaskStep {
    override def execute = Future.successful(this)
  }
  
  trait TextContext extends Scope {
    
    val id = 1l
    val filename = "file name"
    val fileId = 10l
    val mockFile = smartMock[File]
    mockFile.name returns filename
    mockFile.id returns fileId
    
    val mockPdfDocument = smartMock[PdfDocument]
    val texts = Seq("page1", "page2", "page3")
    
    val createDocumentData = new TestCreateDocumentData
    
    class TestCreateDocumentData extends CreateDocumentData {
      override protected val executor = scala.concurrent.ExecutionContext.global
      
      override protected val documentSetId = id

      override protected val file = mockFile
      override protected val pdfDocument = mockPdfDocument
      override protected val textPages = texts 
      
      override protected val nextStep = NextStep
    }
  }
}