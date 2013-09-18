package org.overviewproject.persistence.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }
import org.overviewproject.tree.orm.File
import org.overviewproject.persistence.orm.Schema

object FileFinder extends Finder {

  type FileFinderResult = FinderResult[File]
  
  def byFileGroup(fileGroupId: Long): FileFinderResult = {
    join(Schema.files, Schema.fileGroupFiles)((f, fgf) =>
      where(fgf.fileGroupId === fileGroupId)
      select(f)
      on(f.id === fgf.fileId)
    )
  }
}