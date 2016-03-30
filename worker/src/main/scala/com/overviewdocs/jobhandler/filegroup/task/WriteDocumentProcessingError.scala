package com.overviewdocs.jobhandler.filegroup.task

import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.database.HasDatabase
import com.overviewdocs.models.tables.DocumentProcessingErrors
import com.overviewdocs.util.{Logger,Textify}

object WriteDocumentProcessingError extends HasDatabase {
  private lazy val logger = Logger.forClass(getClass)

  import database.api._

  private lazy val inserter = (DocumentProcessingErrors.map(e => (e.documentSetId, e.textUrl, e.message)))

  def apply(documentSetId: Long, filename: String, message: String)(implicit ec: ExecutionContext): Future[Unit] = {
    logger.info("Error processing {} in docset {}: {}", filename, documentSetId, message)

    /*
     * [adam, 2016-03-30] Somehow, it's possible for a PDF error to contain
     * \u0000. I have no idea how, and I have no idea whether we should be
     * escaping that text _here_. I guess the logic is, we should be able to
     * handle any error message.
     *
     * Really, I'm frustrated by Postgres. What does it have against \u0000,
     * anyway?
     */
    val escapedMessage = Textify(message)

    database.runUnit(inserter.+=((documentSetId, filename, escapedMessage)))
  }
}
