package org.overviewproject.database.orm.finders

import org.overviewproject.database.orm.Schema
import org.overviewproject.tree.orm.File
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.FileJobState._
object FileFinder extends FinderById[File](Schema.files) {
  
  def countByFinishedState(fileGroupId: Long): Long = {
    join(Schema.fileGroupFiles, Schema.files)((fgf, f) => 
      where(fgf.fileGroupId === fileGroupId and 
           ((f.state === Complete) or (f.state === Error)))
      compute(count)
      on(fgf.fileId === f.id)
    )
  }
}