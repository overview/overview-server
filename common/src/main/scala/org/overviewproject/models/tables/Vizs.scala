package org.overviewproject.models.tables

import java.sql.Timestamp
import play.api.libs.json.{Json,JsObject}

import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.Viz

class VizsImpl(tag: Tag) extends Table[Viz](tag, "viz") {
  private implicit val postgres91JsonTextColumnType = MappedColumnType.base[JsObject, String](
    Json.stringify,
    Json.parse(_).as[JsObject]
  )

  def id = column[Long]("id", O.PrimaryKey)
  def documentSetId = column[Long]("document_set_id")
  def url = column[String]("url") // simpler than java.net.URI
  def apiToken = column[String]("api_token") // a password, not a foreign key
  def title = column[String]("title")
  def createdAt = column[Timestamp]("created_at")
  def json = column[JsObject]("json_text")

  def * = (id, documentSetId, url, apiToken, title, createdAt, json) <> (Viz.tupled, Viz.unapply)
}

object Vizs extends TableQuery(new VizsImpl(_))
