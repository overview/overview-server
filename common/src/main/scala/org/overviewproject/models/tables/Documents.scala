package org.overviewproject.models.tables

import java.util.Date // should be java.time.LocalDateTime

import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.Document

class DocumentsImpl(tag: Tag) extends Table[Document](tag, "document") {
  implicit val keywordColumnType = MappedColumnType.base[Seq[String], String](
    _.mkString(" "),
    _.split(" ").toSeq
  )

  implicit val dateColumnType = MappedColumnType.base[Date, java.sql.Timestamp](
    d => new java.sql.Timestamp(d.getTime),
    d => new Date(d.getTime)
  )

  def id = column[Long]("id", O.PrimaryKey)
  def documentSetId = column[Long]("document_set_id")
  def keywords = column[Seq[String]]("description")(keywordColumnType)
  def createdAt = column[Date]("created_at")(dateColumnType)
  def text = column[Option[String]]("text")
  def url = column[Option[String]]("url")
  def suppliedId = column[Option[String]]("supplied_id")
  def documentcloudId = column[Option[String]]("documentcloud_id")
  def title = column[Option[String]]("title")
  def fileId = column[Option[Long]]("file_id")
  def pageId = column[Option[Long]]("page_id")
  def pageNumber = column[Option[Int]]("page_number")

  /*
   * Unfortunately, our database allows NULL in some places it shouldn't. Slick
   * can only handle this with a column[Option[_]] -- no type mappers allowed.
   * So we have to map the types here.
   */
  def * = (
    id,
    documentSetId,
    url,
    suppliedId,
    documentcloudId,
    title,
    pageNumber,
    keywords,
    createdAt,
    fileId,
    pageId,
    text
  ).<>[Document,Tuple12[Long,Long,Option[String],Option[String],Option[String],Option[String],Option[Int],Seq[String],Date,Option[Long],Option[Long],Option[String]]](
    (t: Tuple12[Long,Long,Option[String],Option[String],Option[String],Option[String],Option[Int],Seq[String],Date,Option[Long],Option[Long],Option[String]]) => Document.apply(
      t._1,
      t._2,
      t._3,
      t._4.orElse(t._5).getOrElse(""), // suppliedId || documentcloudId || ""
      t._6.getOrElse(""),              // title
      t._7,
      t._8,
      t._9,
      t._10,
      t._11,
      t._12.getOrElse("")              // text
    ),
    { d: Document => Some(
      d.id,
      d.documentSetId,
      d.url,
      Some(d.suppliedId),
      None,
      Some(d.title),
      d.pageNumber,
      d.keywords,
      d.createdAt,
      d.fileId,
      d.pageId,
      Some(d.text)
    )}
  )
}

object Documents extends TableQuery(new DocumentsImpl(_))
