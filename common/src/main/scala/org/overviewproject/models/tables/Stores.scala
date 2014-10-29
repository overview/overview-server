package org.overviewproject.models.tables

import play.api.libs.json.{JsObject,Json}

import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.Store

class StoresImpl(tag: Tag) extends Table[Store](tag, "store") {
  private implicit val postgres91JsonTextColumnType = MappedColumnType.base[JsObject, String](
    Json.stringify,
    Json.parse(_).as[JsObject]
  )

  def id = column[Long]("id", O.PrimaryKey)
  def apiToken = column[String]("api_token")
  def json = column[JsObject]("json_text")

  def * = (id, apiToken, json) <> (Store.tupled, Store.unapply)
}

object Stores extends TableQuery(new StoresImpl(_))
