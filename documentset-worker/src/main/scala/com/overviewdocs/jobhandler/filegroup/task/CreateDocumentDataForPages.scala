package com.overviewdocs.jobhandler.filegroup.task

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
import com.overviewdocs.util.Textify

class CreateDocumentDataForPages(
  file: File,
  onProgress: Double => Boolean
)(implicit ec: ExecutionContext) extends HasDatabase {
  import database.api._

  private def onSplitProgress(pageNumber: Int, nPages: Int): Boolean = onProgress(0.5 * pageNumber / nPages)
  private def onWriteProgress(pageNumber: Int, nPages: Int): Boolean = onProgress(0.5 + 0.5 * pageNumber / nPages)

  def execute: Future[Either[String,Seq[IncompleteDocument]]] = {
    BlobStorage.withBlobInTempFile(file.viewLocation) { file =>
      PdfSplitter.splitPdf(file.toPath, true, onSplitProgress)
    }
      .flatMap(_ match {
        case Left(message) => Future.successful(Left(message))
        case Right(pageInfos) => {
          for {
            result <- writePagesAndBuildDocuments(pageInfos)
            _ <- Future(blocking(pageInfos.flatMap(_.pdfPath).foreach(JFiles.delete _)))
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
  private def writePagesAndBuildDocuments(pageInfos: Seq[PdfSplitter.PageInfo]): Future[Either[String,Seq[IncompleteDocument]]] = {
    val ret = mutable.ArrayBuffer[IncompleteDocument]()
    val it = pageInfos.iterator

    def step: Future[Boolean] = { // true if completed, false if user cancelled
      if (!it.hasNext) {
        Future.successful(true)
      } else {
        if (!onWriteProgress(ret.length, pageInfos.length)) {
          Future.successful(false)
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

  private def writePageAndBuildDocument(pageInfo: PdfSplitter.PageInfo): Future[IncompleteDocument] = {
    for {
      location <- BlobStorage.create(BlobBucketId.PageData, pageInfo.pdfPath.get)
      nBytes <- Future(blocking(JFiles.size(pageInfo.pdfPath.get)))
      pageId <- database.run(pageInserter.+=(Page.CreateAttributes(
        file.id,
        pageInfo.pageNumber,
        location,
        nBytes,
        pageInfo.text,
        pageInfo.isFromOcr
      )))
    } yield IncompleteDocument(
      filename=file.name,
      pageNumber=Some(pageInfo.pageNumber),
      createdAt=Instant.now,
      fileId=Some(file.id),
      pageId=Some(pageId),
      displayMethod=DocumentDisplayMethod.page,
      isFromOcr=pageInfo.isFromOcr,
      text=pageInfo.text
    )
  }

  private val pageInserter = (Pages.map(_.createAttributes) returning Pages.map(_.id))
}

object CreateDocumentDataForPages {
  def apply(
    file: File,
    onProgress: Double => Boolean
  )(implicit ec: ExecutionContext): Future[Either[String,Seq[IncompleteDocument]]] = {
    new CreateDocumentDataForPages(file, onProgress).execute
  }
}
