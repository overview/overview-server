package org.overviewproject.database

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.slick.jdbc.StaticQuery.interpolation
import org.overviewproject.database.Slick.simple._

trait TempFileDeleter extends SlickClient {

  def delete(documentSetId: Long): Future[Unit] = db { implicit session =>

     releaseFiles(documentSetId)
  }
  
  
  private def releaseFiles(documentSetId: Long)(implicit session: Session): Unit = {
    val fileReferences = sqlu"""
      WITH ids AS (
        SELECT id
        FROM file
        WHERE id IN (SELECT file_id FROM temp_document_set_file WHERE document_set_id = $documentSetId)
        FOR UPDATE
      )
      UPDATE file
      SET reference_count = reference_count - 1
      WHERE id IN (SELECT id FROM ids)
    """

    fileReferences.execute
  }
  
  
}