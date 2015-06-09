package org.overviewproject.jobhandler.filegroup.task.step

import scala.concurrent.Future

import org.overviewproject.database.{HasDatabase,DatabaseProvider}
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

object WriteDocumentProcessingError {
  private val writer: WriteDocumentProcessingError = new WriteDocumentProcessingError with DatabaseProvider
  
  def apply(documentSetId: Long, filename: String, message: String): Future[Unit] = {
    writer.write(documentSetId, filename, message)
  }
}
