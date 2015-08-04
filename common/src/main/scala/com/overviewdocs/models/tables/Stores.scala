package com.overviewdocs.models.tables

import play.api.libs.json.JsObject

import com.overviewdocs.database.Slick.api._
import com.overviewdocs.models.Store

class StoresImpl(tag: Tag) extends Table[Store](tag, "store") {
  def id = column[Long]("id", O.PrimaryKey)
  def apiToken = column[String]("api_token")
  def json = column[JsObject]("json_text")(jsonTextColumnType)

  def * = (id, apiToken, json) <> (Store.tupled, Store.unapply)
}

object Stores extends TableQuery(new StoresImpl(_))
