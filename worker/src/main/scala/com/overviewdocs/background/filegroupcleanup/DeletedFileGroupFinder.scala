package com.overviewdocs.background.filegroupcleanup

import scala.concurrent.Future

import com.overviewdocs.database.HasDatabase
import com.overviewdocs.models.tables.FileGroups

trait DeletedFileGroupFinder {
  def indexIds: Future[Iterable[Long]]
}

/** Finds FileGroup IDs that we need to delete.
  *
  * This finds deleted FileGroups, but *not* FileGroups that have an
  * `addToDocumentSetId` set. (We can't delete those without delving into the
  * add-documents innards; the add-documents logic will delete them.)
  */
object DeletedFileGroupFinder extends DeletedFileGroupFinder with HasDatabase {
  import database.api._

  lazy val query = FileGroups.filter(_.deleted).filter(_.addToDocumentSetId.isEmpty).map(_.id)

  override def indexIds: Future[Iterable[Long]] = database.seq(query)
}
