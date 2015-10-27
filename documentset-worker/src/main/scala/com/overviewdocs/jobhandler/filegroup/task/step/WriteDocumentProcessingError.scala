package com.overviewdocs.jobhandler.filegroup.task.step

import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.database.HasDatabase
import com.overviewdocs.jobhandler.filegroup.task.FilePipelineParameters
import com.overviewdocs.models.tables.DocumentProcessingErrors
import com.overviewdocs.util.Logger

object WriteDocumentProcessingError extends HasDatabase {
  private lazy val logger = Logger.forClass(getClass)

  import database.api._

  private lazy val inserter = (DocumentProcessingErrors.map(e => (e.documentSetId, e.textUrl, e.message)))

  def apply(message: String, params: FilePipelineParameters)(implicit ec: ExecutionContext): Future[Unit] = {
    logger.info("Error processing {} in docset {}: {}", params.filename, params.documentSetId, message)

    database.runUnit(inserter.+=((params.documentSetId, params.filename, message)))
  }
}
