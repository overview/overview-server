package org.overviewproject.models.tables

import play.api.libs.json.{Json,JsObject}

import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.StoreObject

class StoreObjectsImpl(tag: Tag) extends Table[StoreObject](tag, "store_object") {
  private implicit val postgres91JsonTextColumnType = MappedColumnType.base[JsObject, String](
    Json.stringify,
    Json.parse(_).as[JsObject]
  )

  def id = column[Long]("id", O.PrimaryKey)
  def storeId = column[Long]("store_id")
  def indexedLong = column[Option[Long]]("indexed_long")
  def indexedString = column[Option[String]]("indexed_string")
  def json = column[JsObject]("json_text")

  def * = (id, storeId, indexedLong, indexedString, json) <> ((StoreObject.apply _).tupled, StoreObject.unapply)
}

object StoreObjects extends TableQuery(new StoreObjectsImpl(_))
