package org.overviewproject.jobhandler.filegroup.task.step

import scala.collection.SeqView
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.blocking
import scala.util.control.Exception.ultimately

import org.overviewproject.jobhandler.filegroup.task.PdfBoxDocument
import org.overviewproject.jobhandler.filegroup.task.PdfDocument
import org.overviewproject.jobhandler.filegroup.task.PdfPage
import org.overviewproject.models.File

/**
 * Split a [[File]] with a PDF view into [[Page]]s
 */
trait CreatePdfPages extends UploadedFileProcessStep {

  protected val file: File

  override protected val documentSetId: Long
  override protected lazy val filename: String = file.name

  protected val pdfProcessor: PdfProcessor

  protected val pageSaver: PageSaver

  protected val nextStep: Seq[DocumentData] => TaskStep

  protected trait Storage {
    def savePages(fileId: Long, pdfPages: Seq[PdfPage]): Seq[Long]
  }

  override protected def doExecute: Future[TaskStep] =
    loadDocument(file.viewLocation).flatMap { pdfDocument =>
      ultimately(pdfDocument.close) { 
        nextStepWithPages(pdfDocument)
      }
    }

  private def loadDocument(location: String): Future[PdfDocument] = AsFuture {
    pdfProcessor.loadFromBlobStorage(location)
  }

  private def getPages(pdfDocument: PdfDocument): SeqView[PdfPage, Seq[_]] =
    blocking {
      pdfDocument.pages
    }

  private def nextStepWithPages(pdfDocument: PdfDocument): Future[TaskStep] = for {
    pageAttributes <- pageSaver.savePages(file.id, getPages(pdfDocument))
    pageDocumentData = pageAttributes.map(p =>
      PdfPageDocumentData(file.name, file.id, p.pageNumber, p.id, p.text))
  } yield nextStep(pageDocumentData) 

  trait PdfProcessor {
    def loadFromBlobStorage(location: String): PdfDocument
  }
}

object CreatePdfPages {

  def apply(documentSetId: Long, file: File, nextStep: Seq[DocumentData] => TaskStep): CreatePdfPages =
    new CreatePdfPagesImpl(documentSetId, file, nextStep)

  private class CreatePdfPagesImpl(
    override protected val documentSetId: Long,
    override protected val file: File,
    override protected val nextStep: Seq[DocumentData] => TaskStep) extends CreatePdfPages {

    override protected val pageSaver: PageSaver = PageSaver
    override protected val pdfProcessor: PdfProcessor = new PdfProcessorImpl

    private class PdfProcessorImpl extends PdfProcessor {
      override def loadFromBlobStorage(location: String): PdfDocument = new PdfBoxDocument(location)
    }

  }
}