package org.overviewproject.models.tables

import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.Page

class PagesImpl(tag: Tag) extends Table[Page](tag, "page") {

  def id = column[Long]("id", O.PrimaryKey)
  def fileId = column[Long]("file_id")
  def pageNumber = column[Int]("page_number")
  def referenceCount = column[Int]("reference_count")
  def data = column[Array[Byte]]("data")
  def text = column[Option[String]]("text")
  def dataErrorMessage = column[Option[String]]("data_error_message")
  def textErrorMessage = column[Option[String]]("text_error_message")

  def * = (
    id,
    fileId,
    pageNumber,
    referenceCount,
    data.?,
    text,
    dataErrorMessage,
    textErrorMessage) <> (Page.tupled, Page.unapply)
}

object Pages extends TableQuery(new PagesImpl(_))