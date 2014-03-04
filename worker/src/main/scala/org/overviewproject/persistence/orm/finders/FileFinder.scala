package org.overviewproject.persistence.orm.finders

import scala.language.{ implicitConversions, postfixOps }
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }
import org.overviewproject.tree.orm.GroupedProcessedFile
import org.overviewproject.persistence.orm.Schema
import org.squeryl.Query

object FileFinder extends Finder {

  class FileFinderResult(query: Query[GroupedProcessedFile]) extends FinderResult(query) {
    def orderedById: FileFinderResult = 
      from(query)(f =>
        select(f)
        orderBy(f.id asc))
  }
  implicit private def queryToFileFinderResult(query: Query[GroupedProcessedFile]): FileFinderResult = 
    new FileFinderResult(query)
  
  def byFileGroup(fileGroupId: Long): FileFinderResult = 
    Schema.groupedProcessedFiles.where(f => f.fileGroupId === fileGroupId)
}