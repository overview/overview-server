package org.overviewproject.models.tables

import java.sql.Timestamp
import play.api.libs.json.{Json,JsObject}

import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.View

class ViewsImpl(tag: Tag) extends Table[View](tag, "view") {
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

  def * = (id, documentSetId, url, apiToken, title, createdAt) <> ((View.apply _).tupled, View.unapply)
}

object Views extends TableQuery(new ViewsImpl(_))
