package org.overviewproject.jobhandler.filegroup.task.step

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import org.overviewproject.models.File
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import org.overviewproject.jobhandler.filegroup.task.PdfDocument

class ExtractTextWithrOcrSpec extends Specification with Mockito {

  "ExtractTextWithOcr" should {

    "attempt to extract text without OCR" in new PdfWithTextContext {
      val extractTextStep = new TestExtractTextWithOcr

      val r = extractTextStep.execute

      r must be_==(NextStep(documentData)).await
    }

    "start ocr step if needed" in {
      todo
    }
  }

  trait PdfWithTextContext extends Scope {
    val fileName = "file name"
    val viewLocation = "view location"
    val fileId = 1l
    val text = "extracted text"
    val documentData = Seq(PdfFileDocumentData(fileName, fileId, text))
    
    class TestExtractTextWithOcr extends ExtractTextWithOcr {
      override protected val executor: ExecutionContext = implicitly
      override protected val documentSetId = 1l

      override protected val file = smartMock[File]
      file.name returns fileName
      file.viewLocation returns viewLocation
      file.id returns fileId
      
      val pdfDocument = smartMock[PdfDocument]
      pdfDocument.text returns text
      override protected val pdfProcessor = smartMock[PdfProcessor]
      pdfProcessor.loadFromBlobStorage(viewLocation) returns Future.successful(pdfDocument)
      
      override protected val nextStep = NextStep(_)
    }
  }

  case class NextStep(documentData: Seq[DocumentData]) extends TaskStep {
    override def execute = Future.successful(this)
  }

}