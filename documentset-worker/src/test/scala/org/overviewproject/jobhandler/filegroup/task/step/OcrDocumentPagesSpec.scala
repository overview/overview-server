package org.overviewproject.jobhandler.filegroup.task.step

import java.awt.image.BufferedImage

import scala.collection.SeqView
import scala.concurrent.Future

import org.overviewproject.jobhandler.filegroup.task.PdfDocument
import org.overviewproject.models.File
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class OcrDocumentPagesSpec extends Specification with Mockito {

  "OcrDocumentPages" should {

    "call next step with document data when all pages have been ocrd" in new CompleteOcrContext {
      val next = ocrDocumentPages.execute

      next must be_==(NextStep(file, pdfDocument, text)).await
    }

    "ocr the next page" in new ContinueOcrContext {
      val next = ocrDocumentPages.execute

      next must be_==(NextPage(Seq.empty.view, text :+ ocrText)).await
    }
  }

  trait OcrContext extends Scope {
    val pdfDocument = smartMock[PdfDocument]
    val file = smartMock[File]
    val filename = "filename"
    val fileId = 1l

    file.name returns filename
    file.id returns fileId

    val text = Seq("initial text")
    val ocrText = "text from ocr"
    
    val textExtractor = smartMock[OcrTextExtractor]
    
    val ocrDocumentPages = new TestOcrDocumentPages(textExtractor, file, pdfDocument, pages, text)

    def pages: SeqView[BufferedImage, Seq[_]]
  }

  trait CompleteOcrContext extends OcrContext {

    override def pages = Seq.empty.view
  }

  trait ContinueOcrContext extends OcrContext {

    def page = smartMock[BufferedImage]
    override def pages = Seq(page).view
    
    textExtractor.extractText(any, any) returns Future.successful(ocrText)
  }

  case class NextStep(file: File, pdfDocument: PdfDocument, text: Seq[String]) extends TaskStep {
    override def execute = Future.successful(this)
  }

  case class NextPage(pages: SeqView[BufferedImage, Seq[_]], text: Seq[String]) extends TaskStep {
    override def execute = Future.successful(this)
  }

  class TestOcrDocumentPages(
    override protected val ocrTextExtractor: OcrTextExtractor,
    override protected val file: File,
    override protected val pdfDocument: PdfDocument,
    override protected val pageImages: SeqView[BufferedImage, Seq[_]],
    override protected val currentText: Seq[String]) extends OcrDocumentPages {

    override protected val documentSetId = 1l
    override protected val language = "swe"
    override implicit protected val executor = scala.concurrent.ExecutionContext.global

    override protected val nextStep = Function.tupled(NextStep)

    override protected val nextPageStep = Function.tupled(NextPage)


  }

}