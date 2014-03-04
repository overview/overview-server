package org.overviewproject.persistence.orm.finders

import scala.language.{ implicitConversions, postfixOps }
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }
import org.overviewproject.tree.orm.GroupedProcessedFile
import org.overviewproject.persistence.orm.Schema
import org.squeryl.Query

object GroupedProcessedFileFinder extends Finder {

  class GroupedProcessedFileFinderResult(query: Query[GroupedProcessedFile]) extends FinderResult(query) {
    def orderedById: GroupedProcessedFileFinderResult = 
      from(query)(f =>
        select(f)
        orderBy(f.id asc))
  }
  implicit private def queryToGroupedProcessedFileFinderResult(query: Query[GroupedProcessedFile]): GroupedProcessedFileFinderResult = 
    new GroupedProcessedFileFinderResult(query)
  
  def byFileGroup(fileGroupId: Long): GroupedProcessedFileFinderResult = 
    Schema.groupedProcessedFiles.where(f => f.fileGroupId === fileGroupId)
}