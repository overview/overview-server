package org.overviewproject.database.orm.finders

import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.DocumentSetCreationJob
import org.overviewproject.database.orm.Schema
import org.overviewproject.tree.orm.DocumentSetCreationJobState._
import org.overviewproject.tree.DocumentSetCreationJobType._

object DocumentSetCreationJobFinder extends Finder {

  type DocumentSetCreationJobFinderResult = FinderResult[DocumentSetCreationJob]

  def byDocumentSet(documentSetId: Long): DocumentSetCreationJobFinderResult = 
    Schema.documentSetCreationJobs.where(dscj => dscj.documentSetId === documentSetId)
    
  def byFileGroupId(fileGroupId: Long): DocumentSetCreationJobFinderResult =
    Schema.documentSetCreationJobs.where(dscj => dscj.fileGroupId === fileGroupId)

  def byDocumentSetAndState(documentSetId: Long, state: DocumentSetCreationJobState): DocumentSetCreationJobFinderResult =
    Schema.documentSetCreationJobs.where(dscj => dscj.documentSetId === documentSetId and dscj.state === state)

def byDocumentSetAndStateForUpdate(documentSetId: Long, state: DocumentSetCreationJobState): DocumentSetCreationJobFinderResult =
    Schema.documentSetCreationJobs.where(dscj => dscj.documentSetId === documentSetId and dscj.state === state).forUpdate
    
  def byState(state: DocumentSetCreationJobState): DocumentSetCreationJobFinderResult = 
    Schema.documentSetCreationJobs.where(dscj => dscj.state === state)
    
 def byStateAndType(state: DocumentSetCreationJobState, jobType: DocumentSetCreationJobType): DocumentSetCreationJobFinderResult = 
   Schema.documentSetCreationJobs.where(dscj => dscj.state === state and dscj.jobType === jobType)
    
}