package org.overviewproject.models.tables

import org.overviewproject.database.Slick.api._
import org.overviewproject.models.TempDocumentSetFile

class TempDocumentSetFilesImpl(tag: Tag) extends Table[TempDocumentSetFile](tag, "temp_document_set_file") { 
  def documentSetId = column[Long]("document_set_id")
  def fileId = column[Long]("file_id")
  def pk = primaryKey("temp_document_set_file_pkey", (documentSetId, fileId))
  
  def * = (documentSetId, fileId) <> (TempDocumentSetFile.tupled, TempDocumentSetFile.unapply)
}

object TempDocumentSetFiles extends TableQuery(new TempDocumentSetFilesImpl(_))
