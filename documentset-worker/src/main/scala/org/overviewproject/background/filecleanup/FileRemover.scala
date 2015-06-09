package org.overviewproject.background.filecleanup

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import org.overviewproject.blobstorage.BlobStorage
import org.overviewproject.models.tables.Files
import org.overviewproject.database.{HasDatabase, DatabaseProvider}

/**
 * Delete the file, associated data, including pages.
 */
trait FileRemover extends HasDatabase {
  import databaseApi._

  def deleteFile(fileId: Long): Future[Unit] = {
    val pageRemoval = pageRemover.removeFilePages(fileId)
    val contentRemoval = removeContent(fileId)
    
    for {
      p <- pageRemoval
      c <- contentRemoval
      f <- removeFile(fileId)
    } yield ()
  }

  private def removeContent(fileId: Long): Future[Unit] = {
    val contents = findContentIds(fileId)
    contents.flatMap(deleteContents)
  }

  private def findContentIds(fileId: Long): Future[Option[(String, String)]] = {
    database.option(Files.filter(_.id === fileId).map(f => (f.contentsLocation, f.viewLocation)))
  }

  private def deleteContents(contentIds: Option[(String, String)]): Future[Unit] = {
    contentIds match {
      case Some((cId, vId)) if (cId != vId) => blobStorage.deleteMany(Seq(cId, vId))
      case Some((cId, _)) => blobStorage.delete(cId)
      case _ => Future.successful(())
    }
  }
  
  private def removeFile(fileId: Long): Future[Unit] = {
    database.delete(Files.filter(_.id === fileId))
  }

  protected val pageRemover: PageRemover
  protected val blobStorage: BlobStorage
}

object FileRemover {
  def apply(): FileRemover = new FileRemoverImpl
  
  private class PageRemoverImpl extends PageRemover with DatabaseProvider {
    override protected val blobStorage = BlobStorage
  }
  
  private class FileRemoverImpl extends FileRemover with DatabaseProvider {
    override protected val blobStorage = BlobStorage
    override protected val pageRemover = new PageRemoverImpl
  }
}
