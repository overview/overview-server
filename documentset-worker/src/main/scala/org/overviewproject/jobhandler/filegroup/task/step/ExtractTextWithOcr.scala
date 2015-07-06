package org.overviewproject.jobhandler.filegroup.task.step

import scala.collection.SeqView
import scala.concurrent.Future
import scala.util.control.Exception.ultimately
import org.overviewproject.models.File

import org.overviewproject.jobhandler.filegroup.task.PdfPage
import org.overviewproject.jobhandler.filegroup.task.PdfDocument



trait ExtractTextWithOcr extends UploadedFileProcessStep {

  protected val file: File
  override protected lazy val filename = file.name

  protected val nextStep: Seq[DocumentData] => TaskStep
  protected def startOcr(file: File, pages: SeqView[PdfPage, Seq[_]]): TaskStep

  protected val pdfProcessor: PdfProcessor

  protected trait PdfProcessor {
    def loadFromBlobStorage(location: String): Future[PdfDocument]
  }

  override protected def doExecute: Future[TaskStep] = for {
    pdfDocument <- pdfProcessor.loadFromBlobStorage(file.viewLocation)
  } yield ultimately(pdfDocument.close) {
    pdfDocument.textWithFonts.fold(
        _ => startOcr(file, pdfDocument.pages),
        startNextStep)
    
  }

      
  private def startNextStep(text: String): TaskStep = {
    val documentInfo = Seq(PdfFileDocumentData(file.name, file.id, text)) 
    nextStep(documentInfo)
  }
}