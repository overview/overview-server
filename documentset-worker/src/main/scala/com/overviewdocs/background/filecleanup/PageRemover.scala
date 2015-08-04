package com.overviewdocs.background.filecleanup

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.overviewdocs.blobstorage.BlobStorage
import com.overviewdocs.models.tables.Pages
import com.overviewdocs.database.HasDatabase

/**
 *  Delete pages and the data they refer to
 */
trait PageRemover extends HasDatabase {
  import database.api._

  def removeFilePages(fileId: Long): Future[Unit] = 
    for {
      d <- deletePageData(fileId)
      p <- deletePages(fileId)
    } yield ()
    
  private def pageQuery(fileId: Long) = Pages.filter(_.fileId === fileId)

  private def deletePageData(fileId: Long): Future[Unit] =
    findDataLocations(fileId).flatMap(blobStorage.deleteMany)

  private def findDataLocations(fileId: Long): Future[Seq[String]] = {
    database.seq(pageQuery(fileId).map(_.dataLocation))
      .map(_.flatten)
  }
  
  def deletePages(fileId: Long): Future[Unit] = {
    database.delete(pageQuery(fileId))
  }

  protected val blobStorage: BlobStorage
}
