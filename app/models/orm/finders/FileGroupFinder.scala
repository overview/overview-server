package models.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.finders.Finder
import org.overviewproject.tree.orm.finders.FinderResult
import org.overviewproject.tree.orm.FileGroup
import org.overviewproject.tree.orm.FileJobState._
import models.orm.Schema

object FileGroupFinder extends Finder {
  type FileGroupFinderResult = FinderResult[FileGroup]
  
  def byUserAndState(userEmail: String, state: FileJobState): FileGroupFinderResult = 
    Schema.fileGroups.where(fg => fg.userEmail === userEmail and fg.state === InProgress)

}