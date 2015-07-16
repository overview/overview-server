package org.overviewproject.jobhandler.filegroup.task.step

import org.overviewproject.models.File
import scala.collection.SeqView
import org.overviewproject.jobhandler.filegroup.task.PdfPage
import scala.concurrent.Future
import java.awt.image.BufferedImage
import scala.concurrent.ExecutionContext
import org.overviewproject.jobhandler.filegroup.task.TimeoutGenerator

trait OcrDocumentPages extends UploadedFileProcessStep {
  protected val file: File
  override protected lazy val filename = file.name

  protected val ocrTextExtractor: OcrTextExtractor

  protected val pageImages: SeqView[BufferedImage, Seq[_]]
  protected val language: String
  protected val currentText: String

  protected val nextPageStep: (SeqView[BufferedImage, Seq[_]], String) => TaskStep
  protected val nextStep: Seq[DocumentData] => TaskStep

  override protected def doExecute: Future[TaskStep] =
    pageImages.headOption
      .map(ocrPage)
      .getOrElse(completeOcr)

  private def completeOcr: Future[TaskStep] = Future.successful {
    nextStep(Seq(PdfFileDocumentData(filename, file.id, currentText)))
  }

  private def ocrPage(pageImage: BufferedImage): Future[TaskStep] =
    for {
      text <- ocrTextExtractor.extractText(pageImage, language)
    } yield nextPageStep(pageImages.tail, currentText + text)
}

object OcrDocumentPages {

  def apply(documentSetId: Long, file: File, language: String,
            pageImages: SeqView[BufferedImage, Seq[_]],
            timeoutGenerator: TimeoutGenerator,
            nextStep: Seq[DocumentData] => TaskStep)(implicit executor: ExecutionContext): OcrDocumentPages = {

    val ocrTextExtractor = TesseractOcrTextExtractor(timeoutGenerator)

    new OcrDocumentPagesImpl(documentSetId, file, language, ocrTextExtractor, nextStep,
      pageImages, "")
  }

  private class OcrDocumentPagesImpl(
    override protected val documentSetId: Long,
    override protected val file: File,
    override protected val language: String,
    override protected val ocrTextExtractor: OcrTextExtractor,
    override protected val nextStep: Seq[DocumentData] => TaskStep,

    override protected val pageImages: SeqView[BufferedImage, Seq[_]],

    override protected val currentText: String)(override implicit protected val executor: ExecutionContext) extends OcrDocumentPages {

    override protected val nextPageStep = continueOcr _

    private def continueOcr(remainingPages: SeqView[BufferedImage, Seq[_]], textFromOcr: String): TaskStep =
      new OcrDocumentPagesImpl(documentSetId, file, language, ocrTextExtractor, nextStep, remainingPages, textFromOcr)
  }

}