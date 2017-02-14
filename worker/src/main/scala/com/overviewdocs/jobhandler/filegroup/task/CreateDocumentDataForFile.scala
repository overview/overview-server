package com.overviewdocs.jobhandler.filegroup.task

import java.time.Instant

import org.overviewproject.pdfocr.pdf.PdfDocument
import play.api.libs.json.JsObject

import java.nio.file.{Files=>JFiles}
import scala.concurrent.{ExecutionContext, Future}
import com.overviewdocs.blobstorage.{BlobBucketId, BlobStorage}
import com.overviewdocs.models.{DocumentDisplayMethod, File}
import com.overviewdocs.util.{Configuration, Textify}

class CreateDocumentDataForFile(file: File, onProgress: Double => Boolean)(implicit ec: ExecutionContext) {
  private def onSplitterProgress(nPages: Int, pageNumber: Int): Boolean = onProgress(nPages.toDouble / pageNumber)

  def execute: Future[Either[String,Seq[IncompleteDocument]]] = {
    // false don't split
    BlobStorage.withBlobInTempFile(file.viewLocation) { jFile =>
      PdfSplitter.splitPdf(jFile.toPath, false, onSplitterProgress)

    }
      .flatMap {
        case Right(pageInfos) =>
          val text = pageInfos.map(_.text).mkString("\n\n")
          // If first page, create thumbnail and delete file
          val thumbnailLocation = pageInfos.headOption.flatMap(_.thumbnailPath) match {
            case Some(thumbnailPath) =>
              BlobStorage.create(BlobBucketId.PageData, thumbnailPath)
                .map(Some(_))
                .andThen { case _ => JFiles.delete(thumbnailPath) }
            case None => Future.successful(None)
          }

          thumbnailLocation.map {
            thumbnailLocation =>
              Right(Seq(IncompleteDocument(
                filename = file.name,
                pageNumber = None,
                thumbnailLocation = thumbnailLocation,
                createdAt = Instant.now,
                fileId = Some(file.id),
                pageId = None,
                displayMethod = DocumentDisplayMethod.pdf,
                isFromOcr = pageInfos.map(_.isFromOcr).contains(true),
                text = Textify.truncateToNChars(text, CreateDocumentDataForFile.MaxNCharsPerDocument)
              )))
          }

        case Left(error) => Future.successful(Left(error))
      }
  }
}

object CreateDocumentDataForFile {
  def apply(
    file: File,
    onProgress: Double => Boolean
  )(implicit ec: ExecutionContext): Future[Either[String,Seq[IncompleteDocument]]] = {
    new CreateDocumentDataForFile(file, onProgress).execute
  }

  private val MaxNCharsPerDocument = Configuration.getInt("max_n_chars_per_document")
}
