package org.overviewproject.models.tables

import play.api.libs.json.{Json,JsObject}

import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.DocumentStoreObject

class DocumentStoreObjectsImpl(tag: Tag) extends Table[DocumentStoreObject](tag, "document_store_object") {
  def documentId = column[Long]("document_id")
  def storeObjectId = column[Long]("store_object_id")
  def json = column[Option[JsObject]]("json_text")(jsonTextOptionColumnType)

  def * = (documentId, storeObjectId, json) <> (DocumentStoreObject.tupled, DocumentStoreObject.unapply)
}

object DocumentStoreObjects extends TableQuery(new DocumentStoreObjectsImpl(_))
