package org.overviewproject.database.orm.finders

import scala.language.implicitConversions
import org.overviewproject.database.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.GroupedFileUpload
import org.overviewproject.tree.orm.finders.FinderResult
import org.squeryl.Query

object GroupedFileUploadFinder extends FinderById[GroupedFileUpload](Schema.groupedFileUploads) {
  class GroupedFileUploadFinderResult(query: Query[GroupedFileUpload]) extends FinderResult(query) {
    def toIds: FinderResult[Long] = 
      from(query)(f => select (f.id))
  }
  
  implicit private def queryToGroupedFileUploadFinderResult(query: Query[GroupedFileUpload]): GroupedFileUploadFinderResult = 
    new GroupedFileUploadFinderResult(query)

  def byFileGroup(fileGroup: Long): GroupedFileUploadFinderResult =
    from(Schema.groupedFileUploads)(f => 
      where (f.fileGroupId === fileGroup)
      select (f))

  def countsByFileGroup(fileGroupId: Long): Long = {
    from(Schema.groupedFileUploads)(f =>
      where(f.fileGroupId === fileGroupId)
        compute (count))
  }
}
