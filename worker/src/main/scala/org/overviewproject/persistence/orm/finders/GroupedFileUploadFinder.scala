package org.overviewproject.persistence.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }
import org.overviewproject.tree.orm.GroupedFileUpload
import org.overviewproject.persistence.orm.Schema

object GroupedFileUploadFinder extends Finder {
  type GroupedFileUploadFinderResult = FinderResult[GroupedFileUpload]
  
  def byFileGroup(fileGroup: Long): GroupedFileUploadFinderResult =
    Schema.groupedFileUploads.where(f => f.fileGroupId === fileGroup)  
  

}