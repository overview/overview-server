package org.overviewproject.jobhandler.filegroup.task

import scala.concurrent.Future
import scala.concurrent.ExecutionContext

import org.overviewproject.database.{HasDatabase,DatabaseProvider}
import org.overviewproject.models.tables.{Documents,DocumentProcessingErrors}
import org.overviewproject.models.tables.DocumentSets
import org.overviewproject.util.{BulkDocumentWriter,SortedDocumentIdsRefresher}

trait DocumentSetInfoUpdater extends SortedDocumentIdsRefresher with HasDatabase {
  import databaseApi._

  protected val bulkDocumentWriter: BulkDocumentWriter

  def update(documentSetId: Long)(implicit executionContext: ExecutionContext): Future[Unit] = {
    for {
      _ <- bulkDocumentWriter.flush
      _ <- updateCountsAndIds(documentSetId)
    } yield ()
  }

  private def updateCountsAndIds(documentSetId: Long)(implicit executionContext: ExecutionContext): Future[Unit] = {
    val counts = updateCounts(documentSetId)
    val sortedIds = refreshDocumentSet(documentSetId)

    for {
      _ <- updateCounts(documentSetId)
      _ <- refreshDocumentSet(documentSetId)
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

object DocumentSetInfoUpdater {
  def apply(bulkDocumentWriter: BulkDocumentWriter): DocumentSetInfoUpdater =
    new DocumentSetInfoUpdaterImpl(bulkDocumentWriter)

  private class DocumentSetInfoUpdaterImpl(override protected val bulkDocumentWriter: BulkDocumentWriter)
    extends DocumentSetInfoUpdater with DatabaseProvider
}
