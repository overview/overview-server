package com.overviewdocs.models.tables

import java.util.Date
import com.overviewdocs.database.Slick.api._
import com.overviewdocs.models.{DocumentDisplayMethod,DocumentInfo}

/** READ-ONLY!
  *
  * TODO: figure out how to use a Slick TableQuery.map() instead.
  */
class DocumentInfosImpl(tag: Tag) extends Table[DocumentInfo](tag, "document") {
  implicit val dateColumnType = MappedColumnType.base[Date, java.sql.Timestamp](
    d => new java.sql.Timestamp(d.getTime),
    d => new Date(d.getTime)
  )

  def id = column[Long]("id", O.PrimaryKey)
  def documentSetId = column[Long]("document_set_id")
  def createdAt = column[Date]("created_at")(dateColumnType)
  def url = column[Option[String]]("url")
  def suppliedId = column[Option[String]]("supplied_id")
  def documentcloudId = column[Option[String]]("documentcloud_id")
  def title = column[Option[String]]("title")
  def pageNumber = column[Option[Int]]("page_number")
  def fileId = column[Option[Long]]("file_id")
  def displayMethod = column[Option[DocumentDisplayMethod.Value]]("display_method")
  def isFromOcr = column[Option[Boolean]]("is_from_ocr")
  def thumbnailLocation = column[Option[String]]("thumbnail_location")

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
    createdAt,
    fileId,
    displayMethod,
    isFromOcr,
    thumbnailLocation
  ).<>(
    (t: Tuple12[Long,Long,Option[String],Option[String],Option[String],Option[String],Option[Int],Date,Option[Long],Option[DocumentDisplayMethod.Value],Option[Boolean], Option[String]]) => DocumentInfo.apply(
      t._1,                            // id
      t._2,                            // documentSetId
      t._3,                            // url
      t._4.orElse(t._5).getOrElse(""), // suppliedId || documentcloudId || ""
      t._6.getOrElse(""),              // title
      t._7,                            // pageNumber
      t._8,                            // createdAt
      t._10.getOrElse(DocumentDisplayMethod.auto),
      t._11.getOrElse(false),          // isFromOcr
      t._9.isDefined,                  // hasFileView
      t._12                            // thumbnailLocation
    ),
    { d: DocumentInfo => Some(
      d.id,
      d.documentSetId,
      d.url,
      Some(d.suppliedId),
      None,
      Some(d.title),
      d.pageNumber,
      d.createdAt,
      None,
      Some(d.displayMethod),
      Some(d.isFromOcr),
      d.thumbnailLocation
    )}
  )
}

/** READ-ONLY!
  *
  * TODO: figure out how to use a Slick TableQuery.map() instead.
  */
object DocumentInfos extends TableQuery(new DocumentInfosImpl(_))
