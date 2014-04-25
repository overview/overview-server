package org.overviewproject.persistence.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }
import org.overviewproject.tree.orm.File
import org.overviewproject.persistence.orm.Schema.files

object FileFinder extends Finder {
  type FileFinderResult = FinderResult[File]
  
  def byId(id: Long): FileFinderResult = {
    from(files)(f =>
      where (f.id === id)
      select (f)
    )
    
  }

}