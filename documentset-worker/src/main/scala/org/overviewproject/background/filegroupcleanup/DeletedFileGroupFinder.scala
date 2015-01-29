package org.overviewproject.background.filegroupcleanup

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import org.overviewproject.database.Slick.simple._
import org.overviewproject.database.{ SlickClient, SlickSessionProvider }
import org.overviewproject.models.tables.FileGroups

/** 
 *  Find ids of deleted [[FileGroup]]s
 */
trait DeletedFileGroupFinder extends SlickClient {
  def deletedFileGroupIds: Future[Iterable[Long]] = db { implicit session =>
     FileGroups.filter(_.deleted)
       .map(_.id)
       .list
  }
}

object DeletedFileGroupFinder {
  def apply(): DeletedFileGroupFinder = new DeletedFileGroupFinderImpl
  
  private class DeletedFileGroupFinderImpl extends DeletedFileGroupFinder with SlickSessionProvider
}
