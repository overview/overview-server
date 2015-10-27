package com.overviewdocs.jobhandler.filegroup.task.step

import org.overviewproject.pdfocr.pdf.PdfDocument
import play.api.libs.json.JsObject
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.jobhandler.filegroup.task.FilePipelineParameters
import com.overviewdocs.models.{DocumentDisplayMethod,File}
import com.overviewdocs.util.Textify

class CreateDocumentDataForFile(file: File, params: FilePipelineParameters)(implicit ec: ExecutionContext) {
  def execute: Future[Either[String,Seq[DocumentWithoutIds]]] = {
    getText
      .map { case (text, isFromOcr) =>
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
          isFromOcr=isFromOcr,
          metadataJson=JsObject(Seq()),
          text=text
        )

        Right(Seq(document))
      }
  }

  private def getText: Future[(String,Boolean)] = {
    BlobStorage.withBlobInTempFile(file.viewLocation) { file =>
      PdfDocument.load(file.toPath).flatMap { pdfDocument =>
        val pageTexts = new Array[String](pdfDocument.nPages)
        var isFromOcr = false
        val it = pdfDocument.pages
        def fillRemainingPageTexts: Future[Unit] = if (it.hasNext) {
          it.next.flatMap { pdfPage =>
            pageTexts(pdfPage.pageNumber) = pdfPage.toText
            if (!isFromOcr && pdfPage.isFromOcr) isFromOcr = true
            fillRemainingPageTexts
          }
        } else {
          Future.successful(())
        }

        fillRemainingPageTexts
          .andThen { case _ => pdfDocument.close }
          .map(_ => (Textify(pageTexts.mkString("\n\n")), isFromOcr))
      }
    }
  }
}

object CreateDocumentDataForFile {
  def apply(file: File, params: FilePipelineParameters)(implicit ec: ExecutionContext): Future[Either[String,Seq[DocumentWithoutIds]]] = {
    new CreateDocumentDataForFile(file, params).execute
  }
}
