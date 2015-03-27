package org.overviewproject.jobhandler.filegroup.task.step

import scala.concurrent.Future
import org.overviewproject.models.File
import org.overviewproject.jobhandler.filegroup.task.PdfDocument
import scala.concurrent.Promise
import org.overviewproject.jobhandler.filegroup.task.PdfPage
import scala.util.Try

trait CreatePdfPages extends TaskStep {

  protected val file: File
  protected val pdfProcessor: PdfProcessor

  protected val storage: Storage

  protected val nextStep: Iterable[PdfPageDocumentData] => TaskStep

  protected trait Storage {
    def savePages(fileId: Long, pdfPages: Iterable[PdfPage]): Iterable[Long]
  }

  override def execute: Future[TaskStep] = Future.fromTry {
    Try {

      val pdfDocument = pdfProcessor.loadFromBlobStorage(file.contentsLocation)
      val pdfPages = pdfDocument.pages

      val pageIds = storage.savePages(file.id, pdfPages)
      val idAndText = pageIds.zip(pdfPages.map(_.text)) 
      
      val pageData = for {
        ((id, text), pageNumber) <- idAndText.zipWithIndex
      } yield PdfPageDocumentData(file.name, file.id, pageNumber, id, text )
      
      nextStep(pageData)
    }
  }

  trait PdfProcessor {
    def loadFromBlobStorage(location: String): PdfDocument
  }
}