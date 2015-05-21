package org.overviewproject.jobhandler.filegroup.task.step

import scala.concurrent.ExecutionContext.Implicits.global
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
  protected val nextStep: Seq[PdfFileDocumentData] => TaskStep

  override protected def doExecute: Future[TaskStep] = for {
    documentInfo <- AsFuture(getDocumentInfo)
  } yield nextStep(documentInfo)

  private def getDocumentInfo: Seq[PdfFileDocumentData] = {
    val pdfDocument = pdfProcessor.loadFromBlobStorage(file.viewLocation)

    ultimately(pdfDocument.close) {
      val text = pdfDocument.text
      Seq(PdfFileDocumentData(file.name, file.id, text))
    }
  }

  trait PdfProcessor {
    // should return Future[PdfDocument]
    def loadFromBlobStorage(location: String): PdfDocument
  }
}

object ExtractTextFromPdf {
  import scala.concurrent.blocking
  import org.overviewproject.jobhandler.filegroup.task.PdfBoxDocument

  def apply(documentSetId: Long, file: File, next: Seq[PdfFileDocumentData] => TaskStep): ExtractTextFromPdf =
    new ExtractTextFromPdfImpl(documentSetId, file, next)

  private class ExtractTextFromPdfImpl(
    override protected val documentSetId: Long,
    override protected val file: File,
    override protected val nextStep: Seq[PdfFileDocumentData] => TaskStep) extends ExtractTextFromPdf {
    override protected val pdfProcessor: PdfProcessor = new PdfProcessorImpl

    private class PdfProcessorImpl extends PdfProcessor {
      override def loadFromBlobStorage(location: String): PdfDocument = blocking { new PdfBoxDocument(location) }
    }

  }
}