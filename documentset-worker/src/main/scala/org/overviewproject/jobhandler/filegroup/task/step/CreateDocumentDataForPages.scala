package org.overviewproject.jobhandler.filegroup.task.step

import org.overviewproject.models.File
import org.overviewproject.jobhandler.filegroup.task.PdfDocument
import scala.concurrent.Future
import org.overviewproject.models.Page

trait CreateDocumentDataForPages extends UploadedFileProcessStep {
  protected val file: File
  override protected lazy val filename = file.name

  protected val pdfDocument: PdfDocument
  protected val textPages: Seq[String]

  protected val nextStep: Seq[DocumentData] => TaskStep

  protected val pageSaver: PageSaver

  override protected def doExecute: Future[TaskStep] =
    for {
      pageAttributes <- pageSaver.savePages(file.id, pageData)
    } yield nextStep(pageDocumentData(pageAttributes))

  private def pageData = pdfDocument.pages
    .map(_.data)
    .zip(textPages)

  private def pageDocumentData(pageAttributes: Seq[Page.ReferenceAttributes]): Seq[DocumentData] =
    for {
      pageInfo <- pageAttributes
    } yield PdfPageDocumentData(file.name, file.id, pageInfo.pageNumber, pageInfo.id, pageInfo.text)

}