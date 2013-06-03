package views.json.DocumentList

import play.api.libs.json.{JsValue, Writes}
import play.api.libs.json.Json.toJson

import org.overviewproject.tree.orm.Document
import models.ResultPage
import views.json.helper.ModelJsonConverters.JsonDocument

object show {
  private[DocumentList] def documentToJson(document: Document, nodeIds: Seq[Long], tagIds: Seq[Long]) : JsValue = {
    toJson(Map(
      "id" -> toJson(document.id),
      "description" -> toJson(document.description),
      "title" -> toJson(document.title.getOrElse("")),
      "documentcloud_id" -> toJson(document.documentcloudId),
      "nodeids" -> toJson(nodeIds),
      "tagids" -> toJson(tagIds)
    ))
  }

  def apply(documents: ResultPage[(Document,Seq[Long],Seq[Long])]) = {
    toJson(Map[String,JsValue](
      "documents" -> toJson(documents.items.map(Function.tupled(documentToJson)).toSeq),
      "total_items" -> toJson(documents.pageDetails.totalLength)
    ))
  }
}
