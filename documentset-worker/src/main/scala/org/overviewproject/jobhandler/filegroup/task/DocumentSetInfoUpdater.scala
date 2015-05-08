package org.overviewproject.jobhandler.filegroup.task

import scala.concurrent.Future
import org.overviewproject.database.SlickClient
import scala.concurrent.ExecutionContext
import org.overviewproject.models.tables.Documents
import org.overviewproject.models.tables.DocumentProcessingErrors
import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.tables.DocumentSets
import org.overviewproject.util.SortedDocumentIdsRefresher
import org.overviewproject.database.SlickSessionProvider

trait DocumentSetInfoUpdater extends SortedDocumentIdsRefresher {
  def update(documentSetId: Long)(implicit executionContext: ExecutionContext): Future[Unit] = {
     val counts = updateCounts(documentSetId)
     val sortedIds = refreshDocumentSet(documentSetId)

     for {
       cr <- counts
       sr <- sortedIds
     } yield ()
  }
  
  
  private def updateCounts(documentSetId: Long)(implicit executionContext: ExecutionContext): Future[Unit] =  
    db { implicit session =>
      val numberOfDocuments = Documents
        .filter(_.documentSetId === documentSetId)
        .length.run

      val numberOfDocumentProcessingErrors = DocumentProcessingErrors
        .filter(_.documentSetId === documentSetId)
        .length.run

      DocumentSets
        .filter(_.id === documentSetId)
        .map(ds => (ds.documentCount, ds.documentProcessingErrorCount))
        .update((numberOfDocuments, numberOfDocumentProcessingErrors))
  }

}

object DocumentSetInfoUpdater {
  def apply(): DocumentSetInfoUpdater = new DocumentSetInfoUpdater with SlickSessionProvider
}