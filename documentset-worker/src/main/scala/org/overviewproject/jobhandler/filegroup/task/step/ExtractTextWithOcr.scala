package org.overviewproject.jobhandler.filegroup.task.step

import org.overviewproject.models.File
import scala.concurrent.Future
import scala.util.control.Exception.ultimately

import org.overviewproject.jobhandler.filegroup.task.PdfDocument

trait ExtractTextWithOcr extends UploadedFileProcessStep {

  protected val file: File
  override protected lazy val filename = file.name

  protected val nextStep: Seq[DocumentData] => TaskStep
  protected val pdfProcessor: PdfProcessor

  protected trait PdfProcessor {
    def loadFromBlobStorage(location: String): Future[PdfDocument]
  }

  override protected def doExecute: Future[TaskStep] = for {
    pdfDocument <- pdfProcessor.loadFromBlobStorage(file.viewLocation)
  } yield ultimately(pdfDocument.close) {
    nextStep(Seq(PdfFileDocumentData(file.name, file.id, pdfDocument.text)))
  }
}