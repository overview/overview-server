package org.overviewproject.database

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import org.overviewproject.models.tables.FileGroups
import org.overviewproject.database.Slick.simple._

/**
 * Mark the [[FileGroup]] as deleted, and hope some background process
 * does the actual cleanup, including deleting associated [[GroupedFileUpload]]s
 */
trait FileGroupDeleter extends SlickClient {
  def delete(fileGroupId: Long): Future[Unit] = db { implicit session =>
    FileGroups
      .filter(_.id === fileGroupId)
      .filter(_.deleted === false)
      .map(_.deleted)
      .update(true)
  }
}

object FileGroupDeleter {
  def apply(): FileGroupDeleter = new FileGroupDeleter with SlickSessionProvider
}