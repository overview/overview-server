package views.json

import models.core.Document
import play.api.libs.json.{JsValue, Writes}
import play.api.libs.json.Json.toJson

object DocumentList {

  implicit object JsonDocument extends Writes[Document] {
    def writes(document: Document): JsValue = {
      toJson(Map(
          "id" -> toJson(document.id),
          "title" -> toJson(document.title),
          "tagids" -> toJson(Seq[String]())
          ))
    }
  }
  
  def show(documents: Seq[Document], totalCount: Long): JsValue = {
    toJson(
      Map(
          "documents" -> toJson(documents),
          "total_items" -> toJson(totalCount)
          )    
    )
  }
}