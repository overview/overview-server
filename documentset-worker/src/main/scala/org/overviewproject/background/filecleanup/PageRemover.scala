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
  def deleteFilePages(fileId: Long): Future[Unit] = db { implicit session =>

    val pageLocationQuery = Pages.filter(_.fileId === fileId)
    val pageLocations = pageLocationQuery.map(_.dataLocation).list.flatten

    blobStorage.deleteMany(pageLocations)
    
    pageLocationQuery.delete
  }

  protected val blobStorage: BlobStorage
}