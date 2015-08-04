package com.overviewdocs.database.orm.finders

import com.overviewdocs.tree.orm.finders.{ Finder, FinderResult }
import com.overviewdocs.postgres.SquerylEntrypoint._
import com.overviewdocs.tree.orm.DocumentSetCreationJob
import com.overviewdocs.database.orm.Schema
import com.overviewdocs.tree.orm.DocumentSetCreationJobState._
import com.overviewdocs.tree.DocumentSetCreationJobType._

object DocumentSetCreationJobFinder extends Finder {

  type DocumentSetCreationJobFinderResult = FinderResult[DocumentSetCreationJob]

  def byDocumentSet(documentSetId: Long): DocumentSetCreationJobFinderResult =
    Schema.documentSetCreationJobs.where(dscj => dscj.documentSetId === documentSetId)

  def byFileGroupId(fileGroupId: Long): DocumentSetCreationJobFinderResult =
    Schema.documentSetCreationJobs.where(dscj => dscj.fileGroupId === fileGroupId)

  def byDocumentSetAndState(documentSetId: Long, state: DocumentSetCreationJobState): DocumentSetCreationJobFinderResult =
    Schema.documentSetCreationJobs.where(dscj => dscj.documentSetId === documentSetId and dscj.state === state)

  def byDocumentSetAndTypeForUpdate(documentSetId: Long, jobType: DocumentSetCreationJobType): DocumentSetCreationJobFinderResult =
    Schema.documentSetCreationJobs.where(dscj => 
      dscj.documentSetId === documentSetId and dscj.jobType === jobType).forUpdate

  def byDocumentSetAndStateForUpdate(documentSetId: Long, state: DocumentSetCreationJobState): DocumentSetCreationJobFinderResult =
    Schema.documentSetCreationJobs.where(dscj => 
      dscj.documentSetId === documentSetId and dscj.state === state).forUpdate

  def byState(state: DocumentSetCreationJobState): DocumentSetCreationJobFinderResult =
    Schema.documentSetCreationJobs.where(dscj => dscj.state === state)

  def byStateAndType(state: DocumentSetCreationJobState, jobType: DocumentSetCreationJobType): DocumentSetCreationJobFinderResult =
    Schema.documentSetCreationJobs.where(dscj => dscj.state === state and dscj.jobType === jobType)

}