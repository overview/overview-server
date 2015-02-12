package org.overviewproject.jobhandler.filegroup.task.step

import scala.concurrent.Future
import org.overviewproject.models.Document
import org.overviewproject.jobhandler.filegroup.task.PdfDocument
import org.overviewproject.models.File
import scala.util.{ Failure, Success, Try }



trait ExtractTextFromPdf extends TaskStep {

  protected val file: File
  
  protected val pdfProcessor: PdfProcessor
  protected def nextStep(document: PdfFileDocumentData): TaskStep 
  
  override def execute: Future[TaskStep] = toFuture(nextStep(getDocumentInfo))
  
  private def getDocumentInfo: PdfFileDocumentData = {
    val pdfDocument = pdfProcessor.loadFromBlobStorage(file.contentsLocation)
    val text = pdfDocument.text
    
    PdfFileDocumentData(file.name, file.id, text)
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