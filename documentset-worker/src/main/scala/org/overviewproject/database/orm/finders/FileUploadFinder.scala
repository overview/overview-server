package org.overviewproject.database.orm.finders


import org.overviewproject.database.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }
import org.overviewproject.tree.orm.FileUpload

object FileUploadFinder extends Finder {
  type FileUploadFinderResult = FinderResult[FileUpload]
  
def byId(fileUploadId: Long): FileUploadFinderResult =
  Schema.fileUploads.where(f => f.id === fileUploadId)
}