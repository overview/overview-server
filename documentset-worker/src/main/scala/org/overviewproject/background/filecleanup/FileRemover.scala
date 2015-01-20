package org.overviewproject.background.filecleanup

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.overviewproject.blobstorage.BlobStorage
import org.overviewproject.database.Slick.simple._
import org.overviewproject.database.SlickClient
import org.overviewproject.models.tables.Files

/**
 * Delete the file, associated data, including pages.
 */
trait FileRemover extends SlickClient {

  def deleteFile(fileId: Long): Future[Unit] = {
    val pageRemoval = pageRemover.deleteFilePages(fileId)
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

  private def findContentIds(fileId: Long): Future[Option[(Long, Long)]] = db { implicit session =>
    Files.filter(_.id === fileId).map(f => (f.contentsOid, f.viewOid)).firstOption
  }

  private def deleteContents(contentIds: Option[(Long, Long)]): Future[Unit] =
    contentIds match {
      case Some((cId, vId)) if (cId != vId) => blobStorage.deleteMany(Seq(s"pglo:$cId", s"pglo:$vId"))
      case Some((cId, _)) => blobStorage.delete(s"pglo:$cId")
      case _ => Future.successful(())
    }

  
  private def removeFile(fileId: Long): Future[Unit] = db { implicit session => 
    Files.filter(_.id === fileId).delete  
  }
  
  protected val pageRemover: PageRemover
  protected val blobStorage: BlobStorage
}