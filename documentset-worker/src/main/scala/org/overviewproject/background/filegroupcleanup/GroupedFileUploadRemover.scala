package org.overviewproject.background.filegroupcleanup

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.overviewproject.database.Slick.simple._
import org.overviewproject.database.SlickClient
import org.overviewproject.blobstorage.BlobStorage
import org.overviewproject.models.tables.GroupedFileUploads

trait GroupedFileUploadRemover extends SlickClient {

  def removeUploadsFromFileGroup(fileGroupId: Long): Future[Unit] =
    findContentOids(fileGroupId).flatMap { oids =>
      val contentLocations = oids.map(oid => s"pglo:$oid")
      blobStorage.deleteMany(contentLocations)
    }

  private def uploadQuery(fileGroupId: Long) = GroupedFileUploads.filter(_.fileGroupId === fileGroupId)

  private def findContentOids(fileGroupId: Long): Future[List[Long]] = db { implicit session =>
    uploadQuery(fileGroupId).map(_.contentsOid).list
  }
  protected val blobStorage: BlobStorage
}