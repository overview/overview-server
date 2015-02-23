package org.overviewproject.models.tables

import java.util.Date // should be java.time.LocalDateTime

import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.DocumentInfo

/** READ-ONLY!
  *
  * TODO: figure out how to use a Slick TableQuery.map() instead.
  */
class DocumentInfosImpl(tag: Tag) extends Table[DocumentInfo](tag, "document") {
  private val keywordColumnType = MappedColumnType.base[Seq[String], String](
    _.mkString(" "),
    _.split(",?\\s+").toSeq
  )

  private val dateColumnType = MappedColumnType.base[Date, java.sql.Timestamp](
    d => new java.sql.Timestamp(d.getTime),
    d => new Date(d.getTime)
  )

  def id = column[Long]("id", O.PrimaryKey)
  def documentSetId = column[Long]("document_set_id")
  def keywords = column[Seq[String]]("description")(keywordColumnType)
  def createdAt = column[Date]("created_at")(dateColumnType)
  def url = column[Option[String]]("url")
  def suppliedId = column[Option[String]]("supplied_id")
  def documentcloudId = column[Option[String]]("documentcloud_id")
  def title = column[Option[String]]("title")
  def pageNumber = column[Option[Int]]("page_number")
  def fileId = column[Option[Long]]("file_id")

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
    fileId
  ).<>[DocumentInfo,Tuple10[Long,Long,Option[String],Option[String],Option[String],Option[String],Option[Int],Seq[String],Date,Option[Long]]](
    (t: Tuple10[Long,Long,Option[String],Option[String],Option[String],Option[String],Option[Int],Seq[String],Date,Option[Long]]) => DocumentInfo.apply(
      t._1,
      t._2,
      t._3,
      t._4.orElse(t._5).getOrElse(""), // suppliedId || documentcloudId || ""
      t._6.getOrElse(""),              // title
      t._7,
      t._8,
      t._9,
      t._10.isDefined
    ),
    { d: DocumentInfo => Some(
      d.id,
      d.documentSetId,
      d.url,
      Some(d.suppliedId),
      None,
      Some(d.title),
      d.pageNumber,
      d.keywords,
      d.createdAt,
      None
    )}
  )
}

/** READ-ONLY!
  *
  * TODO: figure out how to use a Slick TableQuery.map() instead.
  */
object DocumentInfos extends TableQuery(new DocumentInfosImpl(_))
