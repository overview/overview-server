package com.overviewdocs.jobhandler.filegroup.task.step

import java.awt.image.BufferedImage
import scala.collection.SeqView
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import com.overviewdocs.jobhandler.filegroup.task.PdfBoxDocument
import com.overviewdocs.jobhandler.filegroup.task.PdfDocument
import com.overviewdocs.jobhandler.filegroup.task.TimeoutGenerator
import com.overviewdocs.models.File
import com.overviewdocs.jobhandler.filegroup.task.PdfPage

trait ExtractTextWithOcr extends UploadedFileProcessStep {

  protected val file: File
  override protected lazy val filename = file.name

  protected def startOcr(file: File, pdfDocument: PdfDocument): TaskStep

  protected val pdfProcessor: PdfProcessor

  protected trait PdfProcessor {
    def loadFromBlobStorage(location: String): Future[PdfDocument]
  }

  override protected def doExecute: Future[TaskStep] = for {
    pdfDocument <- pdfProcessor.loadFromBlobStorage(file.viewLocation)
  } yield startOcr(file, pdfDocument)

}

object ExtractTextWithOcr {

  def apply(documentSetId: Long, file: File, next: ((File, PdfDocument, Seq[String])) => TaskStep,
            language: String, timeoutGenerator: TimeoutGenerator)(implicit executor: ExecutionContext): ExtractTextWithOcr =
    new ExtractTextWithOcrImpl(documentSetId, file, next, language, timeoutGenerator)

  private class ExtractTextWithOcrImpl(
    override protected val documentSetId: Long,
    override protected val file: File,
    nextStep: ((File, PdfDocument, Seq[String])) => TaskStep,
    language: String, timeoutGenerator: TimeoutGenerator)(override implicit protected val executor: ExecutionContext) extends ExtractTextWithOcr {

    override protected val pdfProcessor: PdfProcessor = new PdfProcessorImpl

    private class PdfProcessorImpl extends PdfProcessor {
      override def loadFromBlobStorage(location: String): Future[PdfDocument] =
        PdfBoxDocument.loadFromLocation(location)
    }

    override protected def startOcr(file: File, pdfDocument: PdfDocument): TaskStep = 
      OcrDocumentPages(documentSetId, file, language, pdfDocument, pdfDocument.pages, timeoutGenerator, nextStep)

  }

}