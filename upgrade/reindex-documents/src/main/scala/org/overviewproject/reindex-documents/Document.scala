package com.overviewdocs.upgrade.reindex_documents

import play.api.libs.json.Json

case class Document(
  id: Long,
  documentSetId: Long,
  text: String,
  title: String,
  suppliedId: String
) {
  def toJsonString: String = {
    Json.obj(
      "id" -> id,
      "document_set_id" -> documentSetId,
      "text" -> text,
      "title" -> title,
      "suppliedId" -> suppliedId
    ).toString
  }
}
