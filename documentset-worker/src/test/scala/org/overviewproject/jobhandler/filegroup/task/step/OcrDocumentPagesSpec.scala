package org.overviewproject.jobhandler.filegroup.task.step

import java.awt.image.BufferedImage
import scala.collection.SeqView
import scala.concurrent.Future
import org.overviewproject.jobhandler.filegroup.task.PdfDocument
import org.overviewproject.models.File
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.overviewproject.jobhandler.filegroup.task.PdfPage
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.Random

class OcrDocumentPagesSpec extends Specification with Mockito {

  "OcrDocumentPages" should {

    "call next step with document data when all pages have been ocrd" in new CompleteOcrContext {
      val next = ocrDocumentPages.execute

      next must be_==(NextStep(file, pdfDocument, text)).await
    }

    "ocr the next page" in new ContinueOcrContext {
      val next = ocrDocumentPages.execute

      next must be_==(NextPage(Seq.empty, text :+ ocrText)).await
    }

    "close the processed page" in new ContinueOcrContext {
      Await.ready(ocrDocumentPages.execute, Duration.Inf)

      there was one(page).close
    }

    "Use extracted text if OCR not needed" in new TextFoundContext {
      val next = ocrDocumentPages.execute

      next must be_==(NextPage(Seq.empty, text :+ extractedText)).await
    }

    "OCR page if found text is too small" in new NotEnoughTextFoundContext {
      val next = ocrDocumentPages.execute

      next must be_==(NextPage(Seq.empty, text :+ ocrText)).await
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

    lazy val ocrDocumentPages = new TestOcrDocumentPages(textExtractor, file, pdfDocument, pages, text)

    def pages: Seq[PdfPage]
  }

  trait CompleteOcrContext extends OcrContext {

    override def pages = Seq.empty
  }

  trait ContinueOcrContext extends OcrContext {

    val page = smartMock[PdfPage]
    override def pages = Seq(page)

    page.textWithFonts returns Left("")

    textExtractor.extractText(any, any) returns Future.successful(ocrText)
  }

  trait TextFoundContext extends ContinueOcrContext {
    val pageWithText = smartMock[PdfPage]
    val extractedText = Random.nextString(2 * OcrDocumentPages.MinimumTextSize)

    pageWithText.textWithFonts returns Right(extractedText)

    override def pages = Seq(pageWithText)
  }

  trait NotEnoughTextFoundContext extends ContinueOcrContext {
    val pageWithText = smartMock[PdfPage]
    val extractedText = Random.nextString(OcrDocumentPages.MinimumTextSize - 1)

    pageWithText.textWithFonts returns Right(extractedText)

    override def pages = Seq(pageWithText)
  }

  case class NextStep(file: File, pdfDocument: PdfDocument, text: Seq[String]) extends TaskStep {
    override def execute = Future.successful(this)
  }

  case class NextPage(pages: Seq[PdfPage], text: Seq[String]) extends TaskStep {
    override def execute = Future.successful(this)
  }

  class TestOcrDocumentPages(
    override protected val ocrTextExtractor: OcrTextExtractor,
    override protected val file: File,
    override protected val pdfDocument: PdfDocument,
    override protected val pages: Seq[PdfPage],
    override protected val currentText: Seq[String]) extends OcrDocumentPages {

    override protected val documentSetId = 1l
    override protected val language = "swe"
    override implicit protected val executor = scala.concurrent.ExecutionContext.global

    override protected val nextStep = Function.tupled(NextStep)

    override protected val nextPageStep = Function.tupled(NextPage)

  }

}