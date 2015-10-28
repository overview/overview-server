package com.overviewdocs.jobhandler.filegroup.task

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

import com.overviewdocs.database.HasDatabase
import com.overviewdocs.models.tables.{Documents,DocumentProcessingErrors}
import com.overviewdocs.models.tables.DocumentSets
import com.overviewdocs.util.{BulkDocumentWriter,SortedDocumentIdsRefresher}

trait DocumentSetInfoUpdater extends HasDatabase {
  import database.api._

  protected val sortedDocumentIdsRefresher: SortedDocumentIdsRefresher

  def update(documentSetId: Long)(implicit executionContext: ExecutionContext): Future[Unit] = {
    for {
      _ <- updateCounts(documentSetId)
      _ <- sortedDocumentIdsRefresher.refreshDocumentSet(documentSetId)
    } yield ()
  }

  private def updateCounts(documentSetId: Long)(implicit executionContext: ExecutionContext): Future[Unit] = {
    database.runUnit {
      for {
        nDocuments <- Documents.filter(_.documentSetId === documentSetId).length.result
        nErrors <- DocumentProcessingErrors.filter(_.documentSetId === documentSetId).length.result
        _ <- DocumentSets
          .filter(_.id === documentSetId)
          .map(ds => (ds.documentCount, ds.documentProcessingErrorCount))
          .update((nDocuments, nErrors))
      } yield ()
    }
  }
}

object DocumentSetInfoUpdater extends DocumentSetInfoUpdater {
  override protected val sortedDocumentIdsRefresher = SortedDocumentIdsRefresher
}
