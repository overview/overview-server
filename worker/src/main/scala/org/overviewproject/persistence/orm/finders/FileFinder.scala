package org.overviewproject.persistence.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }
import org.overviewproject.tree.orm.GroupedProcessedFile
import org.overviewproject.persistence.orm.Schema

object FileFinder extends Finder {

  type FileFinderResult = FinderResult[GroupedProcessedFile]
  
  def byFileGroup(fileGroupId: Long): FileFinderResult = 
    Schema.groupedProcessedFiles.where(f => f.fileGroupId === fileGroupId)
}