package org.overviewproject.background.filegroupcleanup

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import org.overviewproject.database.{HasDatabase,DatabaseProvider}
import org.overviewproject.models.tables.FileGroups

/** 
 *  Find ids of deleted [[FileGroup]]s
 */
trait DeletedFileGroupFinder extends HasDatabase {
  import databaseApi._

  def deletedFileGroupIds: Future[Iterable[Long]] = {
    database.seq(FileGroups.filter(_.deleted).map(_.id))
  }
}

object DeletedFileGroupFinder {
  def apply(): DeletedFileGroupFinder = new DeletedFileGroupFinderImpl
  
  private class DeletedFileGroupFinderImpl extends DeletedFileGroupFinder with DatabaseProvider
}
