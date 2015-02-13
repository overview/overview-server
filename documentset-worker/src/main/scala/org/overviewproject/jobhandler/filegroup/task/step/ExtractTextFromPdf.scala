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


object ExtractTextFromPdf {
  import org.overviewproject.jobhandler.filegroup.task.PdfBoxDocument
  
  def apply(documentSetId: Long, file: File): ExtractTextFromPdf = 
    new ExtractTextFromPdfImpl(documentSetId, file)
  
  private class ExtractTextFromPdfImpl(
      documentSetId: Long,
      override protected val file: File) extends ExtractTextFromPdf {
    
    override protected val pdfProcessor: PdfProcessor = new PdfProcessorImpl 
    
    override protected def nextStep(document: PdfFileDocumentData): TaskStep = 
      WriteDocuments(document.toDocument(documentSetId))
      
    private class PdfProcessorImpl extends PdfProcessor {
      override def loadFromBlobStorage(location: String): PdfDocument = new PdfBoxDocument(location)
    }

  }
}