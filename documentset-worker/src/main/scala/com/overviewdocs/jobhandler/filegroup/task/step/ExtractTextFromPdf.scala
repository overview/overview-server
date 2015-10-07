package com.overviewdocs.jobhandler.filegroup.task.step

import play.api.libs.json.JsObject
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.jobhandler.filegroup.task.{PdfBoxDocument,PdfDocument}
import com.overviewdocs.models.{DocumentDisplayMethod,File}

/**
  * Extract the text from a [[File]]'s PDF view.
  */
case class ExtractTextFromPdf(
  override val documentSetId: Long,
  file: File,
  nextStep: Seq[DocumentWithoutIds] => TaskStep
)(implicit override val executor: ExecutionContext) extends UploadedFileProcessStep {
  override protected lazy val filename: String = file.name

  protected def loadPdfDocumentFromBlobStorage(location: String): Future[PdfDocument] = {
    import scala.concurrent.blocking
    blocking(PdfBoxDocument.loadFromLocation(location))
  }

  override protected def doExecute: Future[TaskStep] = {
    for {
      pdfDocument <- loadPdfDocumentFromBlobStorage(file.viewLocation)
    } yield {
      val document = DocumentWithoutIds(
        url=None,
        suppliedId=file.name,
        title=file.name,
        pageNumber=None,
        keywords=Seq(),
        createdAt=new java.util.Date(),
        fileId=Some(file.id),
        pageId=None,
        displayMethod=DocumentDisplayMethod.pdf,
        isFromOcr=false,
        metadataJson=JsObject(Seq()),
        text=pdfDocument.text
      )

      pdfDocument.close
      nextStep(Seq(document))
    }
  }
}
