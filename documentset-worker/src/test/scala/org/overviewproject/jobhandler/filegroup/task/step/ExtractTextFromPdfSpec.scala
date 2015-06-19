package org.overviewproject.jobhandler.filegroup.task.step

import scala.concurrent.ExecutionContext.Implicits.global
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.overviewproject.models.Document
import org.specs2.mock.Mockito
import org.overviewproject.models.File
import scala.concurrent.Future
import org.overviewproject.jobhandler.filegroup.task.PdfDocument
import scala.concurrent.ExecutionContext

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
      
      r must throwA[Exception].await
    }
  }

  trait PdfFileScope extends Scope {
    case class DocumentInfo(documentSetId: Long, title: String, fileId: Option[Long], text: String)

    val viewLocation = "view location"
    val fileName = "file name"
    val fileId = 10l
    val documentFile = smartMock[File]
    documentFile.id returns fileId
    documentFile.name returns fileName
    documentFile.viewLocation returns viewLocation

    val text = "file text"
    val pdfDocument: PdfDocument = setupPdfDocument

    val extractTextFromPdf = new TestExtractFromPdf

    def setupPdfDocument: PdfDocument = {
      val d = smartMock[PdfDocument]

      d.text returns text
    }

    case class NextStep(document: Seq[DocumentData]) extends TaskStep {
      override def execute = Future.successful(this)
    }

    class TestExtractFromPdf extends ExtractTextFromPdf {
      override protected val executor: ExecutionContext = implicitly
      override protected val documentSetId = 1l
      override protected val file = documentFile
      override protected val nextStep = { documentData => NextStep(documentData) }
      override protected val pdfProcessor = smartMock[PdfProcessor]
      override protected def errorHandler(t: Throwable): Unit = {}
      
      pdfProcessor.loadFromBlobStorage(viewLocation) returns Future.successful(pdfDocument)

    }

  }

  trait FailingTextExtractionScope extends PdfFileScope {

    override def setupPdfDocument: PdfDocument = {
      val d = smartMock[PdfDocument]

      d.text throws new RuntimeException("failed")
    }
  }

}