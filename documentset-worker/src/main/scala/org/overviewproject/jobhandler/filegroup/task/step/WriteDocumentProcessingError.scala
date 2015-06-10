package org.overviewproject.jobhandler.filegroup.task.step

import scala.concurrent.Future

import org.overviewproject.database.HasDatabase
import org.overviewproject.models.tables.DocumentProcessingErrors

trait WriteDocumentProcessingError extends HasDatabase {
  import databaseApi._

  def write(documentSetId: Long, filename: String, message: String): Future[Unit] = {
    database.runUnit(
      DocumentProcessingErrors
        .map(dpe => (dpe.documentSetId, dpe.textUrl, dpe.message))
        .+=((documentSetId, filename, message))
    )
  }
}

object WriteDocumentProcessingError extends WriteDocumentProcessingError
