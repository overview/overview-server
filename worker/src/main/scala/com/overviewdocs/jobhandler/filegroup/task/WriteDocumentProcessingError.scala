package com.overviewdocs.jobhandler.filegroup.task

import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.database.HasDatabase
import com.overviewdocs.models.tables.DocumentProcessingErrors
import com.overviewdocs.util.Logger

object WriteDocumentProcessingError extends HasDatabase {
  private lazy val logger = Logger.forClass(getClass)

  import database.api._

  private lazy val inserter = (DocumentProcessingErrors.map(e => (e.documentSetId, e.textUrl, e.message)))

  def apply(documentSetId: Long, filename: String, message: String)(implicit ec: ExecutionContext): Future[Unit] = {
    logger.info("Error processing {} in docset {}: {}", filename, documentSetId, message)

    database.runUnit(inserter.+=((documentSetId, filename, message)))
  }
}
