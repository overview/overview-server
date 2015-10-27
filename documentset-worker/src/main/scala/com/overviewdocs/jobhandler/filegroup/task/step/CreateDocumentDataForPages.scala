package com.overviewdocs.jobhandler.filegroup.task.step

import java.io.ByteArrayInputStream
import org.overviewproject.pdfocr.pdf.{PdfDocument,PdfPage}
import play.api.libs.json.JsObject
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.blobstorage.{BlobBucketId,BlobStorage}
import com.overviewdocs.database.HasDatabase
import com.overviewdocs.jobhandler.filegroup.task.FilePipelineParameters
import com.overviewdocs.models.{DocumentDisplayMethod,File,Page}
import com.overviewdocs.models.tables.Pages
import com.overviewdocs.util.Textify

class CreateDocumentDataForPages(file: File, params: FilePipelineParameters)(implicit ec: ExecutionContext)
extends HasDatabase {
  import database.api._

  def execute: Future[Either[String,Seq[DocumentWithoutIds]]] = {
    BlobStorage.withBlobInTempFile(file.viewLocation) { file =>
      PdfDocument.load(file.toPath).flatMap { pdfDocument =>
        val documents: Array[DocumentWithoutIds] = new Array(pdfDocument.nPages)
        val it = pdfDocument.pages
        def step: Future[Unit] = {
          if (it.hasNext) {
            it.next.flatMap { pdfPage =>
              writePage(pdfPage).flatMap { document =>
                documents(pdfPage.pageNumber) = document
                step
              }
            }
          } else {
            Future.successful(())
          }
        }

        step
          .andThen { case _ => pdfDocument.close }
          .map(_ => Right(documents.toSeq))
      }
    }
  }

  private def writePageToBlobStorage(pdfPage: PdfPage): Future[(String,Long)] = {
    val blob: Array[Byte] = pdfPage.toPdf
    val blobInputStream = new ByteArrayInputStream(blob)

    for {
      location <- BlobStorage.create(BlobBucketId.PageData, blobInputStream, blob.length)
    } yield (location, blob.length)
  }

  private val pageInserter = (Pages.map(_.createAttributes) returning Pages.map(_.referenceAttributes))

  private def writePageToDatabase(pdfPage: PdfPage, blobLocation: String, blobNBytes: Long): Future[Page.ReferenceAttributes] = {
    val createAttributes = Page.CreateAttributes(
      file.id,
      pdfPage.pageNumber + 1,
      blobLocation,
      blobNBytes,
      Textify(pdfPage.toText),
      pdfPage.isFromOcr
     )

    database.run(pageInserter.+=(createAttributes))
  }

  private def writePage(pdfPage: PdfPage): Future[DocumentWithoutIds] = {
    for {
      (blobLocation, blobNBytes) <- writePageToBlobStorage(pdfPage)
      attributes <- writePageToDatabase(pdfPage, blobLocation, blobNBytes)
    } yield DocumentWithoutIds(
      url=None,
      suppliedId=file.name,
      title=file.name,
      pageNumber=Some(attributes.pageNumber),
      keywords=Seq(),
      createdAt=new java.util.Date(),
      fileId=Some(attributes.fileId),
      pageId=Some(attributes.id),
      displayMethod=DocumentDisplayMethod.page,
      isFromOcr=attributes.isFromOcr,
      metadataJson=JsObject(Seq()),
      text=attributes.text
    )
  }
}

object CreateDocumentDataForPages {
  def apply(file: File, params: FilePipelineParameters)(implicit ec: ExecutionContext): Future[Either[String,Seq[DocumentWithoutIds]]] = {
    new CreateDocumentDataForPages(file, params).execute
  }
}
