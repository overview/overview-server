package org.overviewproject.jobhandler.filegroup.task.step

import scala.collection.SeqView
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.language.postfixOps

import org.overviewproject.blobstorage.BlobBucketId
import org.overviewproject.blobstorage.BlobStorage
import org.overviewproject.database.Slick.simple._
import org.overviewproject.database.SlickClient
import org.overviewproject.database.SlickSessionProvider
import org.overviewproject.jobhandler.filegroup.task.PdfPage
import org.overviewproject.models.Page
import org.overviewproject.models.tables.Pages
import org.overviewproject.util.TempFile

/**
 * Store the page data with `BlobStorage`, then store `Page` attributes in the database.
 */
trait PageSaver extends SlickClient {

  protected val pageBlobSaver: PageBlobSaver

  protected trait PageBlobSaver {
    def save(page: PdfPage): Future[String]
  }

  def savePages(fileId: Long, pdfPages: SeqView[PdfPage, Seq[_]]): Future[Seq[Page.ReferenceAttributes]] = {
    import scala.concurrent.ExecutionContext.Implicits.global

    val pageAttributes = for {
      (p, n) <- pdfPages.zipWithIndex
    } yield {
      val size = p.data.length
      val text = p.text
      pageBlobSaver.save(p).map { location =>
        Page.CreateAttributes(fileId, n, location, size, text)
      }
    }

    val allAttributes = Future.sequence(pageAttributes)

    for {
      attributes <- allAttributes
      pageIds <- writeToDatabase(attributes)
    } yield pageIds.zip(attributes).map { p =>
      Page.ReferenceAttributes(p._1, p._2.fileId, p._2.pageNumber, p._2.text)
    }
  }

  import scala.language.postfixOps

  private lazy val pageInserter = {
    val q = Pages.map { p => (p.fileId, p.pageNumber, p.dataLocation, p.dataSize, p.text) }
    (q returning Pages.map(_.id)).insertInvoker
  }

  private def writeToDatabase(pageAttributes: Seq[Page.CreateAttributes]): Future[Seq[Long]] =
    db { implicit session =>
      val attributeTuples = pageAttributes
        .map(p => (p.fileId, p.pageNumber, Some(p.dataLocation), p.dataSize, Some(p.text)))
      

      session.withTransaction {
        pageInserter.insertAll(attributeTuples: _*)
      }
    }
}

object PageSaver extends PageSaver with SlickSessionProvider {

  override protected val pageBlobSaver: PageBlobSaver = new TempFilePageBlobSaver

  private class TempFilePageBlobSaver extends PageBlobSaver {
    // The pages coming in are a view: only one is in memory at any given
    // moment. We'd like to upload them without forcing them into memory;
    // let's write them to temporary files.
    //
    // The tempfile stuff ought to be async

    def save(page: PdfPage): Future[String] = {
      val tempfile = new TempFile
      tempfile.outputStream.write(page.data)
      tempfile.outputStream.close
      BlobStorage.create(BlobBucketId.PageData, tempfile.inputStream, page.data.length)
      // yay, now data won't be in memory any more
    }
  }

}