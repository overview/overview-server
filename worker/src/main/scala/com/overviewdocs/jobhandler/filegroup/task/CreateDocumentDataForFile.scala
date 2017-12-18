package com.overviewdocs.jobhandler.filegroup.task

import akka.actor.ActorRefFactory
import java.time.Instant
import java.nio.file.{Files=>JFiles}
import org.overviewproject.pdfocr.pdf.PdfDocument
import play.api.libs.json.JsObject
import scala.concurrent.{ExecutionContext, Future}

import com.overviewdocs.blobstorage.{BlobBucketId, BlobStorage}
import com.overviewdocs.models.{DocumentDisplayMethod, File}
import com.overviewdocs.pdfocr.SplitPdfAndExtractTextReader
import com.overviewdocs.util.{Configuration, Textify}

class CreateDocumentDataForFile(file: File, onProgress: Double => Boolean)(implicit system: ActorRefFactory) {
  import system.dispatcher

  private def onSplitterProgress(nPages: Int, pageNumber: Int): Boolean = onProgress(nPages.toDouble / pageNumber)

  def execute: Future[Either[String,Seq[IncompleteDocument]]] = {
    BlobStorage.withBlobInTempFile(file.viewLocation) { jFile =>
      PdfSplitter.splitPdf(jFile.toPath, false, onSplitterProgress)
    }
      .flatMap {
        case result: SplitPdfAndExtractTextReader.ReadAllResult.Pages => {
          // If the first page has a thumbnail, save it
          val futureThumbnailLocation: Future[Option[String]] = {
            result.pages.headOption.flatMap(_.thumbnailPath) match {
              case Some(path) => {
                for {
                  location <- BlobStorage.create(BlobBucketId.PageData, path) // TODO create thumbnails bucket
                  _ <- result.cleanupAndDeleteTempDir
                } yield Some(location)
              }
              case None => Future.successful(None)
            }
          }

          val text = result.pages.map(_.text).mkString("\n\n")

          for {
            maybeThumbnailLocation <- futureThumbnailLocation
          } yield Right(Seq(IncompleteDocument(
            filename = file.name,
            pageNumber = None,
            thumbnailLocation = maybeThumbnailLocation,
            createdAt = Instant.now,
            fileId = Some(file.id),
            pageId = None,
            displayMethod = DocumentDisplayMethod.pdf,
            isFromOcr = result.pages.map(_.isFromOcr).contains(true),
            text = Textify.truncateToNChars(text, CreateDocumentDataForFile.MaxNCharsPerDocument)
          )))
        }
        case error: SplitPdfAndExtractTextReader.ReadAllResult.Error => {
          error.cleanupAndDeleteTempDir.map(_ => Left(error.message))
        }
      }
  }
}

object CreateDocumentDataForFile {
  def apply(
    file: File,
    onProgress: Double => Boolean
  )(implicit system: ActorRefFactory): Future[Either[String,Seq[IncompleteDocument]]] = {
    new CreateDocumentDataForFile(file, onProgress).execute
  }

  private val MaxNCharsPerDocument = Configuration.getInt("max_n_chars_per_document")
}
