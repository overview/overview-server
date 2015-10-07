package com.overviewdocs.jobhandler.filegroup.task.step

import play.api.libs.json.JsObject
import scala.collection.SeqView
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.jobhandler.filegroup.task.{PdfBoxDocument,PdfDocument}
import com.overviewdocs.models.{DocumentDisplayMethod,File,Page}

case class CreateDocumentDataForPages(
  override val documentSetId: Long,
  isFromOcr: Boolean,
  nextStep: Seq[DocumentWithoutIds] => TaskStep,
  file: File,
  textPages: Seq[String],
  pageSaver: PageSaver = PageSaver
)(implicit override val executor: ExecutionContext) extends UploadedFileProcessStep {
  override protected val filename = file.name

  protected def loadPdfDocumentFromBlobStorage(location: String): Future[PdfDocument] = {
    PdfBoxDocument.loadFromLocation(location)
  }

  override protected def doExecute: Future[TaskStep] = {
    for {
      pdfDocument <- loadPdfDocumentFromBlobStorage(file.viewLocation)
      pages <- Future.successful(pageData(pdfDocument)) // a lazy Iterable
      pageAttributes <- pageSaver.savePages(file.id, pages)
    } yield {
      val documents: Seq[DocumentWithoutIds] = pageAttributes.map { p => DocumentWithoutIds(
        url=None,
        suppliedId=file.name,
        title=file.name,
        pageNumber=Some(p.pageNumber),
        keywords=Seq(),
        createdAt=new java.util.Date(),
        fileId=Some(file.id),
        pageId=Some(p.id),
        displayMethod=DocumentDisplayMethod.page,
        isFromOcr=isFromOcr,
        metadataJson=JsObject(Seq()),
        text=p.text
      )}
      pdfDocument.close
      nextStep(documents)
    }
  }

  private def pageData(pdfDocument: PdfDocument): Iterable[(Array[Byte],String)] = {
    pdfDocument.pages // an Iterable, lazy
      .map { p =>
        val data = p.data
        p.close
        data
      }
      .zip(textPages)
  }
}
