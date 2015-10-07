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

  protected val pageBlobSaver: PageBlobSaver

  protected trait PageBlobSaver {
    def save(pageData: Array[Byte]): Future[String]
  }

  def savePages(fileId: Long, pageInfo: Iterable[(Array[Byte], String)]): Future[Seq[Page.ReferenceAttributes]] = {
    val pageAttributes = for {
      (p, pageNumberZeroBased) <- pageInfo.zipWithIndex
    } yield {
      val size = p._1.length
      val text = p._2
      pageBlobSaver.save(p._1).map { location =>
        Page.CreateAttributes(fileId, pageNumberZeroBased + 1, location, size, text)
      }
    }

    val allAttributes = Future.sequence(pageAttributes.toSeq)

    for {
      attributes <- allAttributes
      pageIds <- writeToDatabase(attributes)
    } yield pageIds.zip(attributes).map { p =>
      Page.ReferenceAttributes(p._1, p._2.fileId, p._2.pageNumber, p._2.text)
    }
  }

  private lazy val pageInserter = {
    val q = Pages.map { p => (p.fileId, p.pageNumber, p.dataLocation, p.dataSize, p.text) }
    (q returning Pages.map(_.id))
  }

  private def writeToDatabase(pageAttributes: Seq[Page.CreateAttributes]): Future[Seq[Long]] = {
    val attributeTuples = pageAttributes
      .map(p => (p.fileId, p.pageNumber, Some(p.dataLocation), p.dataSize, Some(p.text)))

    database.run(pageInserter.++=(attributeTuples))
  }
}

object PageSaver extends PageSaver {
  override protected val pageBlobSaver: PageBlobSaver = new TempFilePageBlobSaver

  private class TempFilePageBlobSaver extends PageBlobSaver {
    // The pages coming in are a view: only one is in memory at any given
    // moment. We'd like to upload them without forcing them into memory;
    // let's write them to temporary files.
    //
    // The tempfile stuff ought to be async

    def save(pageData: Array[Byte]): Future[String] = {
      val tempfile = new TempFile

      tempfile.outputStream.write(pageData)
      tempfile.outputStream.close
      BlobStorage.create(BlobBucketId.PageData, tempfile.inputStream, pageData.length)
      // yay, now data won't be in memory any more
    }
  }

}
