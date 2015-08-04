package com.overviewdocs.jobhandler.filegroup.task.step

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import com.overviewdocs.jobhandler.filegroup.task.PdfDocument
import com.overviewdocs.models.File

trait CreateDocumentData extends UploadedFileProcessStep {

  protected val file: File
  override protected lazy val filename = file.name

  protected val pdfDocument: PdfDocument
  protected val textPages: Seq[String]

  protected val nextStep: Seq[DocumentData] => TaskStep

  override protected def doExecute: Future[TaskStep] = Future.successful { 
    pdfDocument.close
    val documentData = Seq(PdfFileDocumentData(filename, file.id, combineText))
    nextStep(documentData)
  }

  private def combineText: String = textPages.mkString("")
}

object CreateDocumentData {
  def apply(documentSetId: Long, nextStep: Seq[DocumentData] => TaskStep,
            file: File, pdfDocument: PdfDocument,
            textPages: Seq[String])(implicit executor: ExecutionContext): CreateDocumentData =
    new CreateDocumentDataImpl(documentSetId, nextStep, file, pdfDocument, textPages)

  private class CreateDocumentDataImpl(
    override protected val documentSetId: Long,
    override protected val nextStep: Seq[DocumentData] => TaskStep,
    override protected val file: File,
    override protected val pdfDocument: PdfDocument,
    override protected val textPages: Seq[String])
    (override implicit protected val executor: ExecutionContext) extends CreateDocumentData
}