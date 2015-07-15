package org.overviewproject.jobhandler.filegroup.task.step

import org.overviewproject.models.File
import scala.collection.SeqView
import org.overviewproject.jobhandler.filegroup.task.PdfPage
import scala.concurrent.Future
import java.awt.image.BufferedImage

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