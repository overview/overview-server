package org.overviewproject.database.orm.finders

import org.overviewproject.database.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }
import org.overviewproject.tree.orm.File

object FileFinder extends Finder {
  type FileFinderResult = FinderResult[File]
  
  def byId(fileId: Long): FileFinderResult =
    Schema.files.where(f => f.id === fileId)
}