package org.overviewproject.jobhandler.filegroup.task.step

import java.awt.image.BufferedImage
import scala.collection.SeqView
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.Exception.ultimately
import org.overviewproject.jobhandler.filegroup.task.PdfDocument
import org.overviewproject.jobhandler.filegroup.task.TimeoutGenerator
import org.overviewproject.models.File
import org.overviewproject.jobhandler.filegroup.task.PdfPage

trait OcrDocumentPages extends UploadedFileProcessStep {
  protected val file: File
  override protected lazy val filename = file.name

  protected val ocrTextExtractor: OcrTextExtractor

  protected val pdfDocument: PdfDocument
  protected val pages: Seq[PdfPage]
  protected val language: String
  protected val currentText: Seq[String]

  protected val nextPageStep: ((Seq[PdfPage], Seq[String])) => TaskStep
  protected val nextStep: ((File, PdfDocument, Seq[String])) => TaskStep

  override protected def doExecute: Future[TaskStep] =
    pages.headOption
      .map(ocrThenClosePage)
      .getOrElse(completeOcr)

  private def completeOcr: Future[TaskStep] = Future.successful {
    nextStep(file, pdfDocument, currentText)
  }

  private def ocrThenClosePage(page: PdfPage): Future[TaskStep] =
    ocrPage(page).andThen { case _ => page.close() }

  private def ocrPageImage(image: BufferedImage): Future[TaskStep] = {
    for {
      text <- ocrTextExtractor.extractText(image, language)
    } yield nextPageStep(pages.tail, currentText :+ text) 
  }

  private def ocrPage(page: PdfPage): Future[TaskStep] = 
    for {
      text <- ocrTextExtractor.extractText(page.image, language)
    } yield nextPageStep(pages.tail, currentText :+ text) 
}

object OcrDocumentPages {

  def apply(documentSetId: Long, file: File, language: String,
            pdfDocument: PdfDocument,
            pages: Seq[PdfPage],
            timeoutGenerator: TimeoutGenerator,
            nextStep: ((File, PdfDocument, Seq[String])) => TaskStep)(implicit executor: ExecutionContext): OcrDocumentPages = {

    val ocrTextExtractor = TesseractOcrTextExtractor(timeoutGenerator)

    new OcrDocumentPagesImpl(documentSetId, file, language, ocrTextExtractor, nextStep,
      pdfDocument, pages, Seq.empty)
  }

  private class OcrDocumentPagesImpl(
    override protected val documentSetId: Long,
    override protected val file: File,
    override protected val language: String,
    override protected val ocrTextExtractor: OcrTextExtractor,
    override protected val nextStep: ((File, PdfDocument, Seq[String])) => TaskStep,
    override protected val pdfDocument: PdfDocument,
    override protected val pages: Seq[PdfPage],
    override protected val currentText: Seq[String])(override implicit protected val executor: ExecutionContext) extends OcrDocumentPages {

    override protected val nextPageStep = Function.tupled(continueOcr _)

    private def continueOcr(remainingPages: Seq[PdfPage], textFromOcr: Seq[String]): TaskStep =
      new OcrDocumentPagesImpl(documentSetId, file, language, ocrTextExtractor, nextStep, pdfDocument, remainingPages, textFromOcr)

  }

}