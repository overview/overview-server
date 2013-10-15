package org.overviewproject.database.orm.finders

import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.DocumentSetCreationJob
import org.overviewproject.database.orm.Schema
import org.overviewproject.tree.orm.DocumentSetCreationJobState._

object DocumentSetCreationJobFinder extends Finder {

  type DocumentSetCreationJobFinderResult = FinderResult[DocumentSetCreationJob]

  def byFileGroupId(fileGroupId: Long): DocumentSetCreationJobFinderResult =
    Schema.documentSetCreationJobs.where(dscj => dscj.fileGroupId === fileGroupId)

  def byDocumentSetAndState(documentSetId: Long, state: DocumentSetCreationJobState): DocumentSetCreationJobFinderResult =
    Schema.documentSetCreationJobs.where(dscj => dscj.documentSetId === documentSetId and dscj.state === state)
    
}