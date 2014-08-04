package org.overviewproject.models.tables

import org.overviewproject.database.Slick.simple._
import org.overviewproject.tree.orm.Document // should be models.Document

class DocumentsImpl(tag: Tag) extends Table[Document](tag, "document") {
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
  ) <> ((Document.apply _).tupled, Document.unapply)
}

object Documents extends TableQuery(new DocumentsImpl(_))
