package org.overviewproject.models.tables

import org.overviewproject.database.Slick.api._
import org.overviewproject.models.Page

class PagesImpl(tag: Tag) extends Table[Page](tag, "page") {
  def id = column[Long]("id", O.PrimaryKey)
  def fileId = column[Long]("file_id")
  def pageNumber = column[Int]("page_number")
  def dataLocation = column[Option[String]]("data_location")
  def dataSize = column[Long]("data_size")
  def data = column[Option[Array[Byte]]]("data")
  def text = column[Option[String]]("text")
  def dataErrorMessage = column[Option[String]]("data_error_message")
  def textErrorMessage = column[Option[String]]("text_error_message")

  // Syntax nightmare because dataLocation is an Option[String] in the database
  // and we want the outcome here to be a String with a default that depends on
  // the id.
  def * = (
    id,
    fileId,
    pageNumber,
    dataLocation,
    dataSize,
    data,
    text,
    dataErrorMessage,
    textErrorMessage
  ).<>[Page,Tuple9[Long,Long,Int,Option[String],Long,Option[Array[Byte]],Option[String],Option[String],Option[String]]](
    { (x: Tuple9[Long,Long,Int,Option[String],Long,Option[Array[Byte]],Option[String],Option[String],Option[String]]) =>
      Page(x._1, x._2, x._3, x._4.getOrElse("pagebytea:" + x._1), x._5, x._6, x._7, x._8, x._9)
    },
    { (p: Page) => Some(
      p.id,
      p.fileId,
      p.pageNumber,
      Some(p.dataLocation),
      p.dataSize,
      p.data,
      p.text,
      p.dataErrorMessage,
      p.textErrorMessage
    ) }
  )
}

object Pages extends TableQuery(new PagesImpl(_))
