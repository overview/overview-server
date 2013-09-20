package org.overviewproject.database.orm.finders

import org.overviewproject.database.orm.Schema
import org.overviewproject.tree.orm.File
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.FileJobState._
import org.overviewproject.tree.orm.finders.FinderResult

object FileFinder extends FinderById[File](Schema.files) {
  type FileFinderResult = FinderResult[File]

  def byFinishedState(fileGroupId: Long): FileFinderResult =
    Schema.files.where(f =>
      f.fileGroupId === fileGroupId and
        ((f.state === Complete) or (f.state === Error)))
}