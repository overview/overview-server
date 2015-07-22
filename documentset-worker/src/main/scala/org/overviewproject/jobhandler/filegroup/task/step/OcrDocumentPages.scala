package org.overviewproject.jobhandler.filegroup.task.step

import java.awt.image.BufferedImage

import scala.collection.SeqView
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.overviewproject.jobhandler.filegroup.task.PdfDocument
import org.overviewproject.jobhandler.filegroup.task.TimeoutGenerator
import org.overviewproject.models.File

trait OcrDocumentPages extends UploadedFileProcessStep {
  protected val file: File
  override protected lazy val filename = file.name

  protected val ocrTextExtractor: OcrTextExtractor

  protected val pdfDocument: PdfDocument
  protected val pageImages: SeqView[BufferedImage, Seq[_]]
  protected val language: String
  protected val currentText: Seq[String]

  protected val nextPageStep: ((SeqView[BufferedImage, Seq[_]], Seq[String])) => TaskStep
  protected val nextStep: ((File, PdfDocument, Seq[String])) => TaskStep

  override protected def doExecute: Future[TaskStep] =
    pageImages.headOption
      .map(ocrPage)
      .getOrElse(completeOcr)

  private def completeOcr: Future[TaskStep] = Future.successful {
    nextStep(file, pdfDocument, currentText)
  }

  private def ocrPage(pageImage: BufferedImage): Future[TaskStep] =
    for {
      text <- ocrTextExtractor.extractText(pageImage, language)
    } yield nextPageStep(pageImages.tail, currentText :+ text)
}

object OcrDocumentPages {

  def apply(documentSetId: Long, file: File, language: String,
            pdfDocument: PdfDocument,
            pageImages: SeqView[BufferedImage, Seq[_]],
            timeoutGenerator: TimeoutGenerator,
            nextStep: ((File, PdfDocument, Seq[String])) => TaskStep)(implicit executor: ExecutionContext): OcrDocumentPages = {

    val ocrTextExtractor = TesseractOcrTextExtractor(timeoutGenerator)

    new OcrDocumentPagesImpl(documentSetId, file, language, ocrTextExtractor, nextStep,
      pdfDocument, pageImages, Seq.empty)
  }

  private class OcrDocumentPagesImpl(
    override protected val documentSetId: Long,
    override protected val file: File,
    override protected val language: String,
    override protected val ocrTextExtractor: OcrTextExtractor,
    override protected val nextStep: ((File, PdfDocument, Seq[String])) => TaskStep,
    override protected val pdfDocument: PdfDocument,
    override protected val pageImages: SeqView[BufferedImage, Seq[_]],
    override protected val currentText: Seq[String])(override implicit protected val executor: ExecutionContext) extends OcrDocumentPages {

    override protected val nextPageStep = Function.tupled(continueOcr _)

    private def continueOcr(remainingPages: SeqView[BufferedImage, Seq[_]], textFromOcr: Seq[String]): TaskStep = 
      new OcrDocumentPagesImpl(documentSetId, file, language, ocrTextExtractor, nextStep, pdfDocument, remainingPages, textFromOcr)

  }

}