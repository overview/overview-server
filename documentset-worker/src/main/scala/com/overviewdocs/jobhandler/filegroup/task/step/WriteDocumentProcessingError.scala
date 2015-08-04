package com.overviewdocs.jobhandler.filegroup.task.step

import scala.concurrent.Future

import com.overviewdocs.database.HasDatabase
import com.overviewdocs.models.tables.DocumentProcessingErrors

trait WriteDocumentProcessingError extends HasDatabase {
  import database.api._

  def write(documentSetId: Long, filename: String, message: String): Future[Unit] = {
    database.runUnit(
      DocumentProcessingErrors
        .map(dpe => (dpe.documentSetId, dpe.textUrl, dpe.message))
        .+=((documentSetId, filename, message))
    )
  }
}

object WriteDocumentProcessingError extends WriteDocumentProcessingError
