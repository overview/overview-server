package org.overviewproject.jobhandler.filegroup.task.step

import org.overviewproject.database.SlickClient
import scala.concurrent.Future
import org.overviewproject.database.SlickSessionProvider
import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.tables.DocumentProcessingErrors

trait WriteDocumentProcessingError extends SlickClient {
  def write(documentSetId: Long, filename: String, message: String): Future[Unit] = db { implicit session =>
    DocumentProcessingErrors
      .map(dpe => (dpe.documentSetId, dpe.textUrl, dpe.message))
      .insert((documentSetId, filename, message))
  }
}

object WriteDocumentProcessingError {
  private val writer: WriteDocumentProcessingError = new WriteDocumentProcessingError with SlickSessionProvider 
  
  def apply(documentSetId: Long, filename: String, message: String): Future[Unit] = 
    writer.write(documentSetId, filename, message)
}