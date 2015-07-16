package org.overviewproject.jobhandler.filegroup.task.step

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import org.specs2.specification.Scope
import org.overviewproject.models.File
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import org.overviewproject.jobhandler.filegroup.task.PdfDocument
import scala.collection.SeqView
import org.overviewproject.jobhandler.filegroup.task.PdfPage
import java.awt.image.BufferedImage

class ExtractTextWithrOcrSpec extends Specification with Mockito {

  "ExtractTextWithOcr" should {

    "attempt to extract text without OCR" in new PdfWithTextContext {

      val r = extractTextStep.execute

      r must be_==(NextStep(documentData)).await
    }

    "start ocr step if needed" in new PdfWithNoTextContext {
      val r = extractTextStep.execute

      r must be_==(OcrStep(pdfFile, pages)).await
    }
  }

  trait PdfContext extends Scope {
    val fileName = "file name"
    val viewLocation = "view location"
    val fileId = 1l
    val pdfFile = smartMock[File]
    pdfFile.name returns fileName
    pdfFile.viewLocation returns viewLocation
    pdfFile.id returns fileId

    class TestExtractTextWithOcr(document: PdfDocument) extends ExtractTextWithOcr {
      override protected val executor: ExecutionContext = implicitly
      override protected val documentSetId = 1l
      override protected val file = pdfFile
      
      override protected val pdfProcessor = smartMock[PdfProcessor]
      pdfProcessor.loadFromBlobStorage(viewLocation) returns Future.successful(document)

      override protected val nextStep = NextStep(_)
      override protected def startOcr(f: File, p: SeqView[BufferedImage, Seq[_]]): TaskStep = OcrStep(f, p)
    }

  }

  trait PdfWithTextContext extends PdfContext {
    val text = "extracted text"
    val documentData = Seq(PdfFileDocumentData(fileName, fileId, text))
    val document = smartMock[PdfDocument]
    document.textWithFonts returns Right(text)

    val extractTextStep = new TestExtractTextWithOcr(document)

  }

  trait PdfWithNoTextContext extends PdfContext {
    val document = smartMock[PdfDocument]
    val pages = smartMock[SeqView[BufferedImage, Seq[_]]]
    document.textWithFonts returns Left("")
    document.pageImages returns pages
    
    val extractTextStep = new TestExtractTextWithOcr(document)

  }

  case class NextStep(documentData: Seq[DocumentData]) extends TaskStep {
    override def execute = Future.successful(this)
  }
  case class OcrStep(file: File, pages: SeqView[BufferedImage, Seq[_]]) extends TaskStep {
    override def execute = Future.successful(this)
  }

}