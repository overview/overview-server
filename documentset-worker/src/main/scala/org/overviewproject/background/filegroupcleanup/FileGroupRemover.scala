package org.overviewproject.background.filegroupcleanup

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import org.overviewproject.database.{HasDatabase,DatabaseProvider}
import org.overviewproject.blobstorage.BlobStorage
import org.overviewproject.models.tables.FileGroups

/**
 * Delete [[FileGroup]]s and associated [[GroupedFileUpload]].
 * 
 * Ignores `deleted` flag and assumes caller wants to delete [[FileGroup]] no
 * matter what.
 */
trait FileGroupRemover extends HasDatabase {
  import databaseApi._

  def remove(fileGroupId: Long): Future[Unit] = {
    for {
      g <- groupedFileUploadRemover.removeFileGroupUploads(fileGroupId)
      f <- deleteFileGroup(fileGroupId)
    } yield ()
  }

  private def deleteFileGroup(fileGroupId: Long): Future[Unit] = {
    database.delete(FileGroups.filter(_.id === fileGroupId))
  }

  protected val groupedFileUploadRemover: GroupedFileUploadRemover
  protected val blobStorage: BlobStorage
}

object FileGroupRemover {
  def apply(): FileGroupRemover = new FileGroupRemoverImpl
  
  private class FileGroupRemoverImpl extends FileGroupRemover with DatabaseProvider {
    override protected val groupedFileUploadRemover = GroupedFileUploadRemover()
    override protected val blobStorage = BlobStorage
  }
}
