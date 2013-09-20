package org.overviewproject.persistence.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }
import org.overviewproject.tree.orm.FileUpload
import org.overviewproject.persistence.orm.Schema

object FileUploadFinder extends Finder {
  type FileUploadFinderResult = FinderResult[FileUpload]
  
  def byFileGroup(fileGroup: Long): FileUploadFinderResult =
    Schema.fileUploads.where(f => f.fileGroupId === fileGroup)  
  

}