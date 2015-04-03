package org.overviewproject.jobhandler.filegroup.task.step

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import org.overviewproject.jobhandler.filegroup.task.PdfBoxDocument
import org.overviewproject.jobhandler.filegroup.task.PdfDocument
import org.overviewproject.models.File

trait ExtractTextFromPdf extends TaskStep {

  protected val file: File

  protected val pdfProcessor: PdfProcessor
  protected val nextStep: Seq[PdfFileDocumentData] => TaskStep

  override def execute: Future[TaskStep] = toFuture(nextStep(getDocumentInfo))

  private def getDocumentInfo: Seq[PdfFileDocumentData] = {
    val pdfDocument = pdfProcessor.loadFromBlobStorage(file.contentsLocation)
    val text = pdfDocument.text

    Seq(PdfFileDocumentData(file.name, file.id, text))
  }

  // FIXME: replace with Future.fromTry in Scala 2.11
  private def toFuture(f: => TaskStep): Future[TaskStep] =
    Try(f) match {
      case Success(v) => Future.successful(v)
      case Failure(e) => Future.failed(e)
    }

  trait PdfProcessor {
    // should return Future[PdfDocument]
    def loadFromBlobStorage(location: String): PdfDocument
  }
}

object ExtractTextFromPdf {
  import org.overviewproject.jobhandler.filegroup.task.PdfBoxDocument

  def apply(documentSetId: Long, file: File, next: Seq[PdfFileDocumentData] => TaskStep): ExtractTextFromPdf =
    new ExtractTextFromPdfImpl(documentSetId, file, next)

  private class ExtractTextFromPdfImpl(
    documentSetId: Long,
    override protected val file: File,
    override protected val nextStep: Seq[PdfFileDocumentData] => TaskStep) extends ExtractTextFromPdf {
    override protected val pdfProcessor: PdfProcessor = new PdfProcessorImpl

    private class PdfProcessorImpl extends PdfProcessor {
      override def loadFromBlobStorage(location: String): PdfDocument = new PdfBoxDocument(location)
    }

  }
}