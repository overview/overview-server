package org.overviewproject.models.tables

import play.api.libs.json.JsObject

import org.overviewproject.database.Slick.api._
import org.overviewproject.models.Store

class StoresImpl(tag: Tag) extends Table[Store](tag, "store") {
  def id = column[Long]("id", O.PrimaryKey)
  def apiToken = column[String]("api_token")
  def json = column[JsObject]("json_text")(jsonTextColumnType)

  def * = (id, apiToken, json) <> (Store.tupled, Store.unapply)
}

object Stores extends TableQuery(new StoresImpl(_))
