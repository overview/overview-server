package org.overviewproject.jobhandler.filegroup.task.step

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.control.Exception.ultimately

import org.overviewproject.jobhandler.filegroup.task.PdfBoxDocument
import org.overviewproject.jobhandler.filegroup.task.PdfDocument
import org.overviewproject.models.File

/**
 * Extract the text from a [[File]]'s PDF view.
 */
trait ExtractTextFromPdf extends UploadedFileProcessStep {

  protected val file: File

  override protected val documentSetId: Long
  override protected lazy val filename: String = file.name

  protected val pdfProcessor: PdfProcessor
  protected val nextStep: Seq[DocumentData] => TaskStep

  override protected def doExecute: Future[TaskStep] = for {
    documentInfo <- getDocumentInfo
  } yield nextStep(documentInfo)

  private def getDocumentInfo: Future[Seq[DocumentData]] = for {
    pdfDocument <- pdfProcessor.loadFromBlobStorage(file.viewLocation)
  } yield ultimately(pdfDocument.close) {
    Seq(PdfFileDocumentData(file.name, file.id, pdfDocument.text))
  }

  trait PdfProcessor {
    // should return Future[PdfDocument]
    def loadFromBlobStorage(location: String): Future[PdfDocument]
  }
}

object ExtractTextFromPdf {
  import scala.concurrent.blocking
  import org.overviewproject.jobhandler.filegroup.task.PdfBoxDocument

  def apply(documentSetId: Long, file: File,
            next: Seq[DocumentData] => TaskStep)(implicit executor: ExecutionContext): ExtractTextFromPdf =
    new ExtractTextFromPdfImpl(documentSetId, file, next)

  private class ExtractTextFromPdfImpl(
    override protected val documentSetId: Long,
    override protected val file: File,
    override protected val nextStep: Seq[DocumentData] => TaskStep)(override implicit protected val executor: ExecutionContext) extends ExtractTextFromPdf {
    override protected val pdfProcessor: PdfProcessor = new PdfProcessorImpl

    private class PdfProcessorImpl extends PdfProcessor {
      override def loadFromBlobStorage(location: String): Future[PdfDocument] =
        PdfBoxDocument.loadFromLocation(location)
    }

  }
}