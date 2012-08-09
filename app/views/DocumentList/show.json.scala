package views.json.DocumentList


import models.core.Document
import play.api.libs.json.{JsValue, Writes}
import play.api.libs.json.Json.toJson
import views.json.helper.ModelJsonConverters.JsonDocument

object show {

  def apply(documents: Seq[Document], totalCount: Long): JsValue = {
    toJson(
      Map(
          "documents" -> toJson(documents),
          "total_items" -> toJson(totalCount)
          )    
    )
  }

}