package org.overviewproject.background.filegroupcleanup

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import org.overviewproject.database.{HasDatabase,DatabaseProvider}
import org.overviewproject.blobstorage.BlobStorage
import org.overviewproject.models.tables.GroupedFileUploads


/**
 * Delete [[GroupedFileUpload]]s and their contents.
 */
trait GroupedFileUploadRemover extends HasDatabase {
  import databaseApi._

  def removeFileGroupUploads(fileGroupId: Long): Future[Unit] = {
    for {
      c <- deleteContents(fileGroupId)
      g <- deleteGroupedFileUploads(fileGroupId)  
    } yield ()
  }

  private def uploadQuery(fileGroupId: Long) = GroupedFileUploads.filter(_.fileGroupId === fileGroupId)

  private def deleteContents(fileGroupId: Long): Future[Unit] = {
    findContentOids(fileGroupId).flatMap { oids =>
      val contentLocations = oids.map(oid => s"pglo:$oid")
      blobStorage.deleteMany(contentLocations)
    }
  }

  private def findContentOids(fileGroupId: Long): Future[Seq[Long]] = {
    database.seq(uploadQuery(fileGroupId).map(_.contentsOid))
  }

  private def deleteGroupedFileUploads(fileGroupId: Long): Future[Unit] = {
    database.delete(uploadQuery(fileGroupId))
  }

  protected val blobStorage: BlobStorage
}

object GroupedFileUploadRemover {
  def apply(): GroupedFileUploadRemover = new GroupedFileUploadRemoverImpl

  private class GroupedFileUploadRemoverImpl extends GroupedFileUploadRemover with DatabaseProvider {
    override protected val blobStorage = BlobStorage
  }
}
