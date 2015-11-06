package com.overviewdocs.jobhandler.filegroup

import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.database.HasDatabase
import com.overviewdocs.models.{DocumentSetCreationJob,DocumentSetCreationJobState,DocumentSetCreationJobType}
import com.overviewdocs.models.tables.DocumentSetCreationJobs

object ReclusterJobCreator extends HasDatabase {
  def createReclusterJob(documentSetId: Long)(implicit ec: ExecutionContext): Future[Unit] = {
    import database.api._

    database.runUnit(DocumentSetCreationJobs.map(_.createAttributes).+=(DocumentSetCreationJob.CreateAttributes(
      documentSetId=documentSetId,
      jobType=DocumentSetCreationJobType.Recluster,
      retryAttempts=0,
      lang="",
      suppliedStopWords="",
      importantWords="",
      splitDocuments=false,
      documentcloudUsername=None,
      documentcloudPassword=None,
      contentsOid=None,
      sourceDocumentSetId=None,
      treeTitle=Some("Tree"), // FIXME get a proper title
      treeDescription=Some(""),
      tagId=None,
      state=DocumentSetCreationJobState.NotStarted,
      fractionComplete=0,
      statusDescription="",
      canBeCancelled=true
    )))
  }
}
