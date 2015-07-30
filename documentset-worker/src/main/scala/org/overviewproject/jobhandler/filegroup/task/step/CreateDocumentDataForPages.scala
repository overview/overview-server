package org.overviewproject.jobhandler.filegroup.task.step

import scala.concurrent.Future
import scala.util.control.Exception.ultimately
import org.overviewproject.jobhandler.filegroup.task.PdfDocument
import org.overviewproject.models.File
import org.overviewproject.models.Page
import scala.concurrent.ExecutionContext

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
    } yield ultimately(pdfDocument.close) {
      nextStep(pageDocumentData(pageAttributes))
    }

  private def pageData = {
    val pageBytes = for {
      page <- pdfDocument.pages
    } yield ultimately(page.close) {
      page.data
    }
    pageBytes.view.zip(textPages)
  }

  private def pageDocumentData(pageAttributes: Seq[Page.ReferenceAttributes]): Seq[DocumentData] =
    for {
      pageInfo <- pageAttributes
    } yield PdfPageDocumentData(file.name, file.id, pageInfo.pageNumber, pageInfo.id, pageInfo.text)

}

object CreateDocumentDataForPages {

  def apply(documentSetId: Long, nextStep: Seq[DocumentData] => TaskStep,
            file: File, pdfDocument: PdfDocument,
            textPages: Seq[String])(implicit executor: ExecutionContext): CreateDocumentDataForPages =
    new CreateDocumentDataForPagesImpl(documentSetId, nextStep, file, pdfDocument, textPages)

  private class CreateDocumentDataForPagesImpl(
    override protected val documentSetId: Long,
    override protected val nextStep: Seq[DocumentData] => TaskStep,
    override protected val file: File,
    override protected val pdfDocument: PdfDocument,
    override protected val textPages: Seq[String])(override implicit protected val executor: ExecutionContext) extends CreateDocumentDataForPages {

    override protected val pageSaver = PageSaver
  }
}