package com.overviewdocs.models.tables

import com.overviewdocs.database.Slick.api._
import com.overviewdocs.models.DocumentIdList

class DocumentIdListsImpl(tag: Tag) extends Table[DocumentIdList](tag, "document_id_list") {
  def id = column[Long]("id", O.PrimaryKey)
  def documentSetId = column[Int]("document_set_id")
  def fieldName = column[String]("field_name")
  def document32BitIds = column[Vector[Int]]("document_32bit_ids")

  def * = (
    id,
    documentSetId,
    fieldName,
    document32BitIds
  ) <> ((DocumentIdList.apply _).tupled, DocumentIdList.unapply)
}

object DocumentIdLists extends TableQuery(new DocumentIdListsImpl(_))
