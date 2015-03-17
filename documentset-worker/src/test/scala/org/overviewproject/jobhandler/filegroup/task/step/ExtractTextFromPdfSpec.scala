package org.overviewproject.jobhandler.filegroup.task.step

import scala.concurrent.ExecutionContext.Implicits.global
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.overviewproject.models.Document
import org.specs2.mock.Mockito
import org.overviewproject.models.File
import scala.concurrent.Future
import org.overviewproject.jobhandler.filegroup.task.PdfDocument

class ExtractTextFromPdfSpec extends Specification with Mockito {

  "ExtractTextFromPdf" should {
    
    "generate document with text" in new PdfFileScope {
      
      val r = extractTextFromPdf.execute.map {
        case NextStep(d) => d
      }
      
      r must be_==(List(PdfFileDocumentData(fileName, fileId, text))).await
    }
    
    "return failure on error" in new FailingTextExtractionScope {
       val r = extractTextFromPdf.execute 
       
       r.value must beSome(beFailedTry[TaskStep])
    }
  }

  trait PdfFileScope extends Scope {
    case class DocumentInfo(documentSetId: Long, title: String, fileId: Option[Long], text: String)
    
    val location = "blob location"
    val fileName = "file name"
    val fileId = 10l
    val file = smartMock[File]
    file.id returns fileId
    file.name returns fileName
    file.contentsLocation returns location
    
    val text = "file text"
    val pdfDocument = setupPdfDocument
    
    
    val extractTextFromPdf = new TestExtractFromPdf(file, pdfDocument)
    
    def setupPdfDocument = {
      val d = smartMock[PdfDocument]
      
      d.text returns text
    }
  }
  
  trait FailingTextExtractionScope extends PdfFileScope {
    
    override def setupPdfDocument = {
      val d = smartMock[PdfDocument]
      
      d.text throws new RuntimeException("failed")
    }
  }
  
  case class NextStep(document: Seq[PdfFileDocumentData]) extends TaskStep {
    override def execute = Future.successful(this)
  }
  
  class TestExtractFromPdf(val file: File, pdfDocument: PdfDocument) extends ExtractTextFromPdf {
    override def nextStep(documentData: Seq[PdfFileDocumentData]) = NextStep(documentData)
    override protected val pdfProcessor = smartMock[PdfProcessor]
    
    pdfProcessor.loadFromBlobStorage(any) returns pdfDocument

  }
}