package org.overviewproject.jobhandler.filegroup.task.step

import scala.concurrent.Future
import org.overviewproject.models.File
import org.overviewproject.jobhandler.filegroup.task.PdfDocument
import scala.concurrent.Promise
import org.overviewproject.jobhandler.filegroup.task.PdfPage
import scala.util.Try
import scala.collection.SeqView
import scala.concurrent.ExecutionContext.Implicits.global

trait CreatePdfPages extends TaskStep {

  protected val file: File
  protected val pdfProcessor: PdfProcessor

  protected val pageSaver: PageSaver

  protected val nextStep: Iterable[PdfPageDocumentData] => TaskStep

  protected trait Storage {
    def savePages(fileId: Long, pdfPages: Iterable[PdfPage]): Iterable[Long]
  }

  override def execute: Future[TaskStep] = for {
    pdfPages <- loadPages(file.contentsLocation)
    pageAttributes <- pageSaver.savePages(file.id, pdfPages)
  } yield {
    val pageDocumentData = pageAttributes.map(p =>
      PdfPageDocumentData(file.name, file.id, p.pageNumber, p.id, p.text))

    nextStep(pageDocumentData)
  }

  private def loadPages(location: String): Future[SeqView[PdfPage, Seq[_]]] = Future.fromTry {
    Try {
      val pdfDocument = pdfProcessor.loadFromBlobStorage(location)
      pdfDocument.pages
    }
  }

  trait PdfProcessor {
    def loadFromBlobStorage(location: String): PdfDocument
  }
}