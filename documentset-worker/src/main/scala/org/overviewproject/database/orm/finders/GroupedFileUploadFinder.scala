package org.overviewproject.database.orm.finders


import org.overviewproject.database.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.GroupedFileUpload
import org.overviewproject.tree.orm.finders.FinderResult

object GroupedFileUploadFinder extends FinderById[GroupedFileUpload](Schema.groupedFileUploads) {

  def countsByFileGroup(fileGroupId: Long): Long = {
    from(Schema.groupedFileUploads)(f =>
      where(f.fileGroupId === fileGroupId)
      compute(count))
  }
}
