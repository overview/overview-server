package com.overviewdocs.jobhandler.filegroup.task

import akka.actor.ActorRefFactory
import java.io.ByteArrayInputStream
import java.nio.file.{Files=>JFiles}
import java.time.Instant
import org.overviewproject.pdfocr.pdf.{PdfDocument,PdfPage}
import play.api.libs.json.JsObject
import scala.collection.mutable
import scala.concurrent.{ExecutionContext,Future,blocking}

import com.overviewdocs.blobstorage.{BlobBucketId,BlobStorage}
import com.overviewdocs.database.HasDatabase
import com.overviewdocs.models.{DocumentDisplayMethod,File,Page}
import com.overviewdocs.models.tables.Pages
import com.overviewdocs.pdfocr.SplitPdfAndExtractTextReader
import com.overviewdocs.util.{Configuration,Textify}

class CreateDocumentDataForPages(
  file: File,
  onProgress: Double => Boolean
)(implicit system: ActorRefFactory) extends HasDatabase {
  import system.dispatcher
  import database.api._

  private def onSplitProgress(pageNumber: Int, nPages: Int): Boolean = onProgress(0.5 * pageNumber / nPages)
  private def onWriteProgress(pageNumber: Int, nPages: Int): Boolean = onProgress(0.5 + 0.5 * pageNumber / nPages)

  def execute: Future[Either[String,Seq[IncompleteDocument]]] = {
    BlobStorage.withBlobInTempFile(file.viewLocation) { file =>
      PdfSplitter.splitPdf(file.toPath, true, onSplitProgress)
    }
      .flatMap(_ match {
        case SplitPdfAndExtractTextReader.ReadAllResult.Error(message) => Future.successful(Left(message))
        case splitResult: SplitPdfAndExtractTextReader.ReadAllResult.Pages => {
          for {
            result <- writePagesAndBuildDocuments(splitResult.pages)
            _ <- splitResult.cleanupAndDeleteTempDir
          } yield result
        }
      })
  }

  /** Writes pages to the database.
    *
    * We anticipate no errors -- if anything fails, we should crash.
    *
    * We *do* expect cancellation. If the user cancels, we simply don't go any
    * further. We only abort in between writes to S3/Postgres -- so on restart,
    * the File stranded in the database has as many complete Pages attached to
    * it as possible. (These stranded Files can be deleted, recovering the
    * extra space.)
    */
  private def writePagesAndBuildDocuments(pageInfos: Seq[SplitPdfAndExtractTextReader.ReadOneResult.Page]): Future[Either[String,Seq[IncompleteDocument]]] = {
    val ret = mutable.ArrayBuffer[IncompleteDocument]()
    val it = pageInfos.iterator

    def finish(completed: Boolean): Future[Boolean] = {
      Future.successful(completed)
    }

    def step: Future[Boolean] = { // true if completed, false if user cancelled
      if (!it.hasNext) {
        finish(true)
      } else {
        if (!onWriteProgress(ret.length, pageInfos.length)) {
          finish(false)
        } else {
          writePageAndBuildDocument(it.next).flatMap { incompleteDocument =>
            ret.+=(incompleteDocument)
            step
          }
        }
      }
    }

    step.map(_ match {
      case true => Right(ret.toSeq)
      case false => Left("You cancelled an import job")
    })
  }

  private def writePageAndBuildDocument(pageInfo: SplitPdfAndExtractTextReader.ReadOneResult.Page): Future[IncompleteDocument] = {
    for {
      location <- BlobStorage.create(BlobBucketId.PageData, pageInfo.pdfPath.get)

      // upload thumbnail preview to s3
      thumbnailLocation <- pageInfo.thumbnailPath match{
        case Some(path) => BlobStorage.create(BlobBucketId.PageData, path).map(Some(_))
        case None => Future.successful(None)
      }

      nBytes <- Future(blocking(JFiles.size(pageInfo.pdfPath.get)))
      pageId <- database.run(pageInserter.+=(Page.CreateAttributes(
        file.id,
        pageInfo.pageNumber,
        location,
        nBytes,
        Textify.truncateToNChars(pageInfo.text, CreateDocumentDataForPages.MaxNCharsPerDocument),
        pageInfo.isFromOcr
      )))
    } yield IncompleteDocument(
      filename=file.name,
      pageNumber=Some(pageInfo.pageNumber),
      thumbnailLocation=thumbnailLocation,
      createdAt=Instant.now,
      fileId=Some(file.id),
      pageId=Some(pageId),
      displayMethod=DocumentDisplayMethod.page,
      isFromOcr=pageInfo.isFromOcr,
      text=Textify.truncateToNChars(pageInfo.text, CreateDocumentDataForPages.MaxNCharsPerDocument)
    )
  }

  private val pageInserter = (Pages.map(_.createAttributes) returning Pages.map(_.id))
}

object CreateDocumentDataForPages {
  def apply(
    file: File,
    onProgress: Double => Boolean
  )(implicit system: ActorRefFactory): Future[Either[String,Seq[IncompleteDocument]]] = {
    new CreateDocumentDataForPages(file, onProgress).execute
  }

  private val MaxNCharsPerDocument = Configuration.getInt("max_n_chars_per_document")
}
