package org.overviewproject.database.orm.finders


import org.overviewproject.database.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.FileUpload
import org.overviewproject.tree.orm.finders.FinderResult

object FileUploadFinder extends FinderById[FileUpload](Schema.fileUploads) {

  def countsByFileGroup(fileGroupId: Long): Long = {
    from(Schema.fileUploads)(f =>
      where(f.fileGroupId === fileGroupId)
      compute(count))
  }
}
