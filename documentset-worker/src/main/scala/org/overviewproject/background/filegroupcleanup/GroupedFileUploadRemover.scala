package org.overviewproject.background.filegroupcleanup

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.overviewproject.database.Slick.simple._
import org.overviewproject.database.SlickClient
import org.overviewproject.blobstorage.BlobStorage
import org.overviewproject.models.tables.GroupedFileUploads

trait GroupedFileUploadRemover extends SlickClient {
  
  def removeUploadsFromFileGroup(fileGroupId: Long): Future[Unit]  = db { implicit session =>
    val uploadQuery = GroupedFileUploads.filter(_.fileGroupId === fileGroupId)
    val contentOids = uploadQuery.map(_.contentsOid).list

    val contentLocations = contentOids.map(coid => s"pglo:$coid")

    blobStorage.deleteMany(contentLocations)
  }

  protected val blobStorage: BlobStorage
}