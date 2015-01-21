package org.overviewproject.background.filecleanup

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.overviewproject.database.Slick.simple._
import org.overviewproject.database.SlickClient
import org.overviewproject.models.tables.Files
import org.overviewproject.database.SlickSessionProvider


/**
 * Looks for [[File]]s with `referenceCount == 0`
 */
trait DeletedFileScanner extends SlickClient {
  
  def deletedFileIds: Future[Iterable[Long]] = db { implicit session =>
    Files.filter(_.referenceCount === 0)
      .map(_.id)
      .list
  }

}

object DeletedFileScanner {
  def apply(): DeletedFileScanner = new DeletedFileScannerImpl
  
  class DeletedFileScannerImpl extends DeletedFileScanner with SlickSessionProvider
}