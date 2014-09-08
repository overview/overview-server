package org.overviewproject.database.orm.finders

import org.overviewproject.tree.orm.FileGroup
import org.overviewproject.database.orm.Schema
import org.overviewproject.tree.orm.finders.FinderById
import org.overviewproject.tree.orm.finders.FinderResult
import org.overviewproject.postgres.SquerylEntrypoint._


object FileGroupFinder extends FinderById[FileGroup](Schema.fileGroups) {
  type FileGroupFinderResult = FinderResult[FileGroup]
  
  def byDocumentSetId(documentSetId: Long): FileGroupFinderResult = {
    from(Schema.fileGroups, Schema.documentSetCreationJobs)((f, j) =>
      where(j.documentSetId === documentSetId and nvl(j.fileGroupId, -1) === f.id)
      select(f)
    )
  }
}