package org.overviewproject.database.orm.finders

import org.overviewproject.database.orm.Schema
import org.overviewproject.tree.orm.GroupedProcessedFile
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.FileJobState._
import org.overviewproject.tree.orm.finders.FinderById
import org.overviewproject.tree.orm.finders.FinderResult

object GroupedProcessedFileFinder extends FinderById[GroupedProcessedFile](Schema.groupedProcessedFiles) { 
  type GroupedProcessedFileFinderResult = FinderResult[GroupedProcessedFile]

  def byFileGroup(fileGroupId: Long): GroupedProcessedFileFinderResult =
    Schema.groupedProcessedFiles.where(f => f.fileGroupId === fileGroupId)
    
  def byContentsOid(oid: Long): GroupedProcessedFileFinderResult = 
    Schema.groupedProcessedFiles.where(f => f.contentsOid === oid)
    
}