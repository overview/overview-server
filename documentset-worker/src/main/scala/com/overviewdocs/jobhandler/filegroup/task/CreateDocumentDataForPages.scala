package com.overviewdocs.jobhandler.filegroup.task

import java.io.ByteArrayInputStream
import org.overviewproject.pdfocr.pdf.{PdfDocument,PdfPage}
import play.api.libs.json.JsObject
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.blobstorage.{BlobBucketId,BlobStorage}
import com.overviewdocs.database.HasDatabase
import com.overviewdocs.models.{DocumentDisplayMethod,File,Page}
import com.overviewdocs.models.tables.Pages
import com.overviewdocs.util.Textify

class CreateDocumentDataForPages(
  file: File,
  onProgress: Double => Boolean
)(implicit ec: ExecutionContext) extends HasDatabase {
  import database.api._

  def execute: Future[Either[String,Seq[DocumentWithoutIds]]] = {
    BlobStorage.withBlobInTempFile(file.viewLocation) { file =>
      PdfDocument.load(file.toPath).flatMap { pdfDocument =>
        var documents: Array[DocumentWithoutIds] = new Array(pdfDocument.nPages)
        var nPagesProcessed = 0
        val it = pdfDocument.pages
        def step: Future[Unit] = {
          // progress-report / cancellation
          if (pdfDocument.nPages > 0 && !onProgress(nPagesProcessed.toDouble / pdfDocument.nPages)) {
            documents = Array()
            return Future.successful(())
          }

          if (it.hasNext) {
            it.next.flatMap { pdfPage =>
              writePage(pdfPage).flatMap { document =>
                documents(pdfPage.pageNumber) = document
                nPagesProcessed += 1
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
  def apply(
    file: File,
    onProgress: Double => Boolean
  )(implicit ec: ExecutionContext): Future[Either[String,Seq[DocumentWithoutIds]]] = {
    new CreateDocumentDataForPages(file, onProgress).execute
  }
}
