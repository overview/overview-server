package views.json.helper

import models.core.{Document, DocumentIdList, Tag}
import play.api.libs.json.{JsValue, Writes}
import play.api.libs.json.Json.toJson

object ModelJsonConverters {
  
  implicit object JsonDocumentIdList extends Writes[DocumentIdList] {
    override def writes(documentIdList: DocumentIdList) : JsValue = {
      toJson(Map(
          "docids" -> toJson(documentIdList.firstIds),
          "n" -> toJson(documentIdList.totalCount)
      ))
    }
  }

  implicit object JsonTag extends Writes[Tag] {
    override def writes(tag: Tag): JsValue = {
      toJson(Map(
        "id" -> toJson(tag.id),
        "name" -> toJson(tag.name),
        "doclist" -> toJson(tag.documentIds)
      ))
    }
  }

  implicit object JsonDocument extends Writes[Document] {
    override def writes(document: Document) : JsValue = {
      toJson(Map(
        "id" -> toJson(document.id),
        "title" -> toJson(document.title),
        "tagids" -> toJson(document.tags),
        "documentcloud_id" -> toJson(document.documentCloudId)
      ))
    }
  }

}
