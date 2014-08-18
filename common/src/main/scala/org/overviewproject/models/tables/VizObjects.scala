package org.overviewproject.models.tables

import play.api.libs.json.{Json,JsObject}

import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.VizObject

class VizObjectsImpl(tag: Tag) extends Table[VizObject](tag, "viz_object") {
  private implicit val postgres91JsonTextColumnType = MappedColumnType.base[JsObject, String](
    Json.stringify,
    Json.parse(_).as[JsObject]
  )

  def id = column[Long]("id", O.PrimaryKey)
  def vizId = column[Long]("viz_id")
  def indexedLong = column[Option[Long]]("indexed_long")
  def indexedString = column[Option[String]]("indexed_string")
  def json = column[JsObject]("json_text")

  def * = (id, vizId, indexedLong, indexedString, json) <> ((VizObject.apply _).tupled, VizObject.unapply)
}

object VizObjects extends TableQuery(new VizObjectsImpl(_))
