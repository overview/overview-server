package org.overviewproject.background.filecleanup

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.overviewproject.blobstorage.BlobStorage
import org.overviewproject.database.SlickClient
import org.overviewproject.models.tables.Pages
import org.overviewproject.database.Slick.simple._

/**
 *  Delete pages and the data they refer to
 */
trait PageRemover extends SlickClient {
  def removeFilePages(fileId: Long): Future[Unit] = 
    for {
      d <- deletePageData(fileId)
      p <- deletePages(fileId)
    } yield ()
    
  private def pageQuery(fileId: Long) = Pages.filter(_.fileId === fileId)

  private def deletePageData(fileId: Long): Future[Unit] =
    findDataLocations(fileId).flatMap(blobStorage.deleteMany)

  private def findDataLocations(fileId: Long): Future[List[String]] = db { implicit session =>
    pageQuery(fileId).map(_.dataLocation).list.flatten
  }
  
  def deletePages(fileId: Long): Future[Unit] = db { implicit session =>
    pageQuery(fileId).delete  
  }

  protected val blobStorage: BlobStorage
}