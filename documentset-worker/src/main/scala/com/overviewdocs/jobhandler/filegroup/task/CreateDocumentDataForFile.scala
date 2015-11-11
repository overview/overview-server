package com.overviewdocs.jobhandler.filegroup.task

import org.overviewproject.pdfocr.pdf.PdfDocument
import play.api.libs.json.JsObject
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.models.{DocumentDisplayMethod,File}
import com.overviewdocs.util.Textify

class CreateDocumentDataForFile(file: File, onProgress: Double => Boolean)(implicit ec: ExecutionContext) {
  private def onSplitterProgress(nPages: Int, pageNumber: Int): Boolean = onProgress(nPages.toDouble / pageNumber)

  def execute: Future[Either[String,Seq[DocumentWithoutIds]]] = {
    BlobStorage.withBlobInTempFile(file.viewLocation) { jFile =>
      PdfSplitter.splitPdf(jFile.toPath, false, onSplitterProgress)
    }
      .map(_.right.map { pageInfos =>
        Seq(DocumentWithoutIds(
          url=None,
          suppliedId=file.name,
          title=file.name,
          pageNumber=None,
          keywords=Seq(),
          createdAt=new java.util.Date(),
          fileId=Some(file.id),
          pageId=None,
          displayMethod=DocumentDisplayMethod.pdf,
          isFromOcr=pageInfos.map(_.isFromOcr).contains(true),
          metadataJson=JsObject(Seq()),
          text=pageInfos.map(_.text).mkString("\n\n")
        ))
      })
  }
}

object CreateDocumentDataForFile {
  def apply(
    file: File,
    onProgress: Double => Boolean
  )(implicit ec: ExecutionContext): Future[Either[String,Seq[DocumentWithoutIds]]] = {
    new CreateDocumentDataForFile(file, onProgress).execute
  }
}
