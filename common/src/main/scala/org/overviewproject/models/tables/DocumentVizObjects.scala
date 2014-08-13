package org.overviewproject.models.tables

import play.api.libs.json.{Json,JsObject}

import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.DocumentVizObject

class DocumentVizObjectsImpl(tag: Tag) extends Table[DocumentVizObject](tag, "document_viz_object") {
  private implicit val postgres91JsonTextColumnType = MappedColumnType.base[JsObject, String](
    Json.stringify,
    Json.parse(_).as[JsObject]
  )

  def documentId = column[Long]("document_id")
  def vizObjectId = column[Long]("viz_object_id")
  def json = column[Option[JsObject]]("json_text")

  def * = (documentId, vizObjectId, json) <> (DocumentVizObject.tupled, DocumentVizObject.unapply)
}

object DocumentVizObjects extends TableQuery(new DocumentVizObjectsImpl(_))
