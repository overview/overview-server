package org.overviewproject.jobhandler.filegroup.task.step

import scala.collection.SeqView
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.Exception.ultimately
import org.overviewproject.models.File
import org.overviewproject.jobhandler.filegroup.task.PdfPage
import org.overviewproject.jobhandler.filegroup.task.PdfDocument
import org.overviewproject.jobhandler.filegroup.task.TimeoutGenerator
import org.overviewproject.jobhandler.filegroup.task.PdfBoxDocument
import java.awt.image.BufferedImage

trait ExtractTextWithOcr extends UploadedFileProcessStep {

  protected val file: File
  override protected lazy val filename = file.name

  protected val nextStep: Seq[DocumentData] => TaskStep
  protected def startOcr(file: File, pages: SeqView[BufferedImage, Seq[_]]): TaskStep

  protected val pdfProcessor: PdfProcessor

  protected trait PdfProcessor {
    def loadFromBlobStorage(location: String): Future[PdfDocument]
  }

  override protected def doExecute: Future[TaskStep] = for {
    pdfDocument <- pdfProcessor.loadFromBlobStorage(file.viewLocation)
  } yield ultimately(pdfDocument.close) {
    pdfDocument.textWithFonts.fold(
      _ => startOcr(file, pdfDocument.pageImages),
      startNextStep)

  }

  private def startNextStep(text: String): TaskStep = {
    val documentInfo = Seq(PdfFileDocumentData(file.name, file.id, text))
    nextStep(documentInfo)
  }
}

object ExtractTextWithOcr {

  def apply(documentSetId: Long, file: File, next: Seq[DocumentData] => TaskStep,
            language: String, timeoutGenerator: TimeoutGenerator)(implicit executor: ExecutionContext): ExtractTextWithOcr =
    new ExtractTextWithOcrImpl(documentSetId, file, next, language, timeoutGenerator)

  private class ExtractTextWithOcrImpl(
    override protected val documentSetId: Long,
    override protected val file: File,
    override protected val nextStep: Seq[DocumentData] => TaskStep,
    language: String, timeoutGenerator: TimeoutGenerator)(override implicit protected val executor: ExecutionContext) extends ExtractTextWithOcr {

    override protected val pdfProcessor: PdfProcessor = new PdfProcessorImpl

    private class PdfProcessorImpl extends PdfProcessor {
      override def loadFromBlobStorage(location: String): Future[PdfDocument] =
        PdfBoxDocument.loadFromLocation(location)
    }

    override protected def startOcr(file: File, pages: SeqView[BufferedImage, Seq[_]]): TaskStep =
      OcrDocumentPages(documentSetId, file, language, pages, timeoutGenerator, nextStep)
  }

}