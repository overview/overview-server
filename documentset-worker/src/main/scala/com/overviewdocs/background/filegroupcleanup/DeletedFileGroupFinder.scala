package com.overviewdocs.background.filegroupcleanup

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.overviewdocs.database.HasDatabase
import com.overviewdocs.models.tables.FileGroups

/** 
 *  Find ids of deleted [[FileGroup]]s
 */
trait DeletedFileGroupFinder extends HasDatabase {
  import database.api._

  lazy val query = FileGroups.filter(_.deleted).filter(_.addToDocumentSetId.isEmpty).map(_.id)

  def deletedFileGroupIds: Future[Iterable[Long]] = database.seq(query)
}

object DeletedFileGroupFinder {
  def apply(): DeletedFileGroupFinder = new DeletedFileGroupFinderImpl
  
  private class DeletedFileGroupFinderImpl extends DeletedFileGroupFinder
}
