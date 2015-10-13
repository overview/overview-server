package com.overviewdocs.jobhandler.filegroup.task.step

import play.api.libs.json.JsObject
import scala.collection.SeqView
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.jobhandler.filegroup.task.{PdfBoxDocument,PdfDocument,PdfPage}
import com.overviewdocs.models.{DocumentDisplayMethod,File}

/**
 * Split a [[File]] with a PDF view into [[Page]]s
 */
case class CreatePdfPages(
  override val documentSetId: Long,
  file: File,
  nextStep: Seq[DocumentWithoutIds] => TaskStep,
  pageSaver: PageSaver = PageSaver
)(implicit override val executor: ExecutionContext) extends UploadedFileProcessStep {
  override protected val filename: String = file.name

  protected def loadPdfDocumentFromBlobStorage(location: String): Future[PdfDocument] = {
    PdfBoxDocument.loadFromLocation(location)
  }

  private def getPageData(pdfDocument: PdfDocument): Iterable[(Array[Byte],String,Boolean)] = {
    pdfDocument.pages.map { p =>
      val ret = (p.data, p.text, false)
      p.close
      ret
    }
  }

  override protected def doExecute: Future[TaskStep] = {
    for {
      pdfDocument <- loadPdfDocumentFromBlobStorage(file.viewLocation)
      pages <- Future.successful(getPageData(pdfDocument))
      attributes <- pageSaver.savePages(file.id, pages)
    } yield {
      val documents = attributes.map { p => DocumentWithoutIds(
        url=None,
        suppliedId=file.name,
        title=file.name,
        pageNumber=Some(p.pageNumber),
        keywords=Seq(),
        createdAt=new java.util.Date(),
        fileId=Some(file.id),
        pageId=Some(p.id),
        displayMethod=DocumentDisplayMethod.page,
        isFromOcr=false,
        metadataJson=JsObject(Seq()),
        text=p.text
      )}
      nextStep(documents)
    }
  }
}
