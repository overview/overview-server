package org.overviewproject.models.tables

import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.DocumentInfo

/** READ-ONLY!
  *
  * TODO: figure out how to use a Slick TableQuery.map() instead.
  */
class DocumentInfosImpl(tag: Tag) extends Table[DocumentInfo](tag, "document") {
  private val keywordColumnType = MappedColumnType.base[Seq[String], String](
    _.mkString(" "),
    _.split(" ").toSeq
  )

  def id = column[Long]("id", O.PrimaryKey)
  def documentSetId = column[Long]("document_set_id")
  def keywords = column[Seq[String]]("description")(keywordColumnType)
  def url = column[Option[String]]("url")
  def suppliedId = column[Option[String]]("supplied_id")
  def documentcloudId = column[Option[String]]("documentcloud_id")
  def title = column[Option[String]]("title")
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
    keywords
  ).<>[DocumentInfo,Tuple8[Long,Long,Option[String],Option[String],Option[String],Option[String],Option[Int],Seq[String]]](
    (t: Tuple8[Long,Long,Option[String],Option[String],Option[String],Option[String],Option[Int],Seq[String]]) => DocumentInfo.apply(
      t._1,
      t._2,
      t._3,
      t._4.orElse(t._5).getOrElse(""), // suppliedId || documentcloudId || ""
      t._6.getOrElse(""),              // title
      t._7,
      t._8
    ),
    { d: DocumentInfo => Some(
      d.id,
      d.documentSetId,
      d.url,
      Some(d.suppliedId),
      None,
      Some(d.title),
      d.pageNumber,
      d.keywords
    )}
  )
}

/** READ-ONLY!
  *
  * TODO: figure out how to use a Slick TableQuery.map() instead.
  */
object DocumentInfos extends TableQuery(new DocumentInfosImpl(_))
