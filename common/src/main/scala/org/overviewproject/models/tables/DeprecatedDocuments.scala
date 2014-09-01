package org.overviewproject.models.tables

import org.overviewproject.database.Slick.simple._
import org.overviewproject.tree.orm.{Document => DeprecatedDocument}

class DeprecatedDocumentsImpl(tag: Tag) extends Table[DeprecatedDocument](tag, "document") {
  def id = column[Long]("id", O.PrimaryKey)
  def documentSetId = column[Long]("document_set_id")
  def description = column[String]("description")
  def documentcloudId = column[Option[String]]("documentcloud_id")
  def text = column[Option[String]]("text")
  def url = column[Option[String]]("url")
  def suppliedId = column[Option[String]]("supplied_id")
  def title = column[Option[String]]("title")
  def fileId = column[Option[Long]]("file_id")
  def pageId = column[Option[Long]]("page_id")
  def pageNumber = column[Option[Int]]("page_number")

  def * = (
    documentSetId,
    description,
    title,
    suppliedId,
    text,
    url,
    documentcloudId,
    fileId,
    pageId,
    pageNumber,
    id
  ) <> ((DeprecatedDocument.apply _).tupled, DeprecatedDocument.unapply)
}

object DeprecatedDocuments extends TableQuery(new DeprecatedDocumentsImpl(_))
