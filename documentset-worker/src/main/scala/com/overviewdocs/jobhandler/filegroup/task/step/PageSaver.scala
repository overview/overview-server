package com.overviewdocs.jobhandler.filegroup.task.step

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.overviewdocs.blobstorage.BlobBucketId
import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.database.HasDatabase
import com.overviewdocs.jobhandler.filegroup.task.PdfPage
import com.overviewdocs.models.Page
import com.overviewdocs.models.tables.Pages
import com.overviewdocs.util.TempFile

/**
 * Store the page data with `BlobStorage`, then store `Page` attributes in the database.
 */
trait PageSaver extends HasDatabase {
  import database.api._

  protected val blobStorage: BlobStorage

  def savePages(fileId: Long, pageInfo: Iterable[(Array[Byte], String, Boolean)]): Future[Seq[Page.ReferenceAttributes]] = {
    val pageAttributes = for {
      (p, pageNumberZeroBased) <- pageInfo.zipWithIndex
    } yield {
      val size = p._1.length
      val text = p._2
      val isFromOcr = p._3
      savePageData(p._1).map { location =>
        // No references to p here: that would lead to OutOfMemoryError because
        // p is huge and we can only handle one at a time.
        Page.CreateAttributes(fileId, pageNumberZeroBased + 1, location, size, text, isFromOcr)
      }
    }

    val allAttributes = Future.sequence(pageAttributes.toSeq)

    for {
      attributes <- allAttributes
      pageIds <- writeToDatabase(attributes)
    } yield pageIds.zip(attributes).map { p =>
      Page.ReferenceAttributes(p._1, p._2.fileId, p._2.pageNumber, p._2.text, p._2.isFromOcr)
    }
  }

  /** Saves page data to BlobStorage and returns its location. */
  private def savePageData(data: Array[Byte]): Future[String] = {
    // The pages coming in are a view: only one is in memory at any given
    // moment. If we passed them to BlobStorage as ByteArrayInputStreams, then
    // BlobStorage would keep all page data in memory as it saves it. That
    // leads to OutOfMemoryError.
    //
    // So we'll write data to a temporary file synchronously and pass the
    // InputStream to BlobStorage. That way, `data` vacates RAM right away.

    val tempfile = new TempFile
    tempfile.outputStream.write(data)
    tempfile.outputStream.close
    blobStorage.create(BlobBucketId.PageData, tempfile.inputStream, data.length)
  }

  private lazy val pageInserter = (Pages.map(_.createAttributes) returning Pages.map(_.id))

  private def writeToDatabase(pageAttributes: Seq[Page.CreateAttributes]): Future[Seq[Long]] = {
    database.run(pageInserter.++=(pageAttributes))
  }
}

object PageSaver extends PageSaver {
  override protected val blobStorage = BlobStorage
}
