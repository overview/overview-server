package com.overviewdocs.models.tables

import com.overviewdocs.database.Slick.api._
import com.overviewdocs.models.DocumentSetFile2

class DocumentSetFile2sImpl(tag: Tag) extends Table[DocumentSetFile2](tag, "document_set_file2") {
  def documentSetId = column[Long]("document_set_id")
  def file2Id = column[Long]("file2_id")

  def pk = primaryKey("document_set_file2_pkey", (documentSetId, file2Id))
  def * = (documentSetId, file2Id) <> ((DocumentSetFile2.apply _).tupled, DocumentSetFile2.unapply)
}

object DocumentSetFile2s extends TableQuery(new DocumentSetFile2sImpl(_))
