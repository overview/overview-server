package org.overviewproject.jobhandler.filegroup.task.step

import java.awt.image.BufferedImage

import scala.collection.SeqView
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import org.overviewproject.jobhandler.filegroup.task.PdfBoxDocument
import org.overviewproject.jobhandler.filegroup.task.PdfDocument
import org.overviewproject.jobhandler.filegroup.task.TimeoutGenerator
import org.overviewproject.models.File

trait ExtractTextWithOcr extends UploadedFileProcessStep {

  protected val file: File
  override protected lazy val filename = file.name

  protected val nextStep: ((File, PdfDocument, Seq[String])) => TaskStep
  protected def startOcr(file: File, pdfDocument: PdfDocument, pages: SeqView[BufferedImage, Seq[_]]): TaskStep

  protected val pdfProcessor: PdfProcessor

  protected trait PdfProcessor {
    def loadFromBlobStorage(location: String): Future[PdfDocument]
  }

  override protected def doExecute: Future[TaskStep] = for {
    pdfDocument <- pdfProcessor.loadFromBlobStorage(file.viewLocation)
  } yield pdfDocument.textWithFonts.fold(
    _ => startOcr(file, pdfDocument, pdfDocument.pageImages),
    startNextStep(pdfDocument))

  
  private def startNextStep(pdfDocument: PdfDocument)(text: String): TaskStep = {
    nextStep((file, pdfDocument, Seq(text)))
  }
}

object ExtractTextWithOcr {

  def apply(documentSetId: Long, file: File, next: ((File, PdfDocument, Seq[String])) => TaskStep,
            language: String, timeoutGenerator: TimeoutGenerator)(implicit executor: ExecutionContext): ExtractTextWithOcr =
    new ExtractTextWithOcrImpl(documentSetId, file, next, language, timeoutGenerator)

  private class ExtractTextWithOcrImpl(
    override protected val documentSetId: Long,
    override protected val file: File,
    override protected val nextStep: ((File, PdfDocument, Seq[String])) => TaskStep,
    language: String, timeoutGenerator: TimeoutGenerator)(override implicit protected val executor: ExecutionContext) extends ExtractTextWithOcr {

    override protected val pdfProcessor: PdfProcessor = new PdfProcessorImpl

    private class PdfProcessorImpl extends PdfProcessor {
      override def loadFromBlobStorage(location: String): Future[PdfDocument] =
        PdfBoxDocument.loadFromLocation(location)
    }

    override protected def startOcr(file: File, pdfDocument: PdfDocument, pages: SeqView[BufferedImage, Seq[_]]): TaskStep =
      OcrDocumentPages(documentSetId, file, language, pdfDocument, pages, timeoutGenerator, nextStep)
  }

}