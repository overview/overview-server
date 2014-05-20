package views.json.DocumentList

import play.api.libs.json.JsValue
import play.api.libs.json.Json

import org.overviewproject.tree.orm.Document
import org.overviewproject.tree.orm.finders.ResultPage

object show {
  private def documentToJson(document: Document, nodeIds: Seq[Long], tagIds: Seq[Long]) : JsValue = {
    Json.obj(
      "id" -> document.id,
      "description" -> document.description,
      "title" -> Json.toJson(document.title.getOrElse("")), // beats me
      "documentcloud_id" -> document.documentcloudId,
      "nodeids" -> nodeIds,
      "page_number" -> document.pageNumber,
      "tagids" -> tagIds
    )
  }

  def apply(documents: ResultPage[(Document,Seq[Long],Seq[Long])]) = {
    Json.obj(
      "documents" -> documents.items.map(Function.tupled(documentToJson)).toSeq,
      "total_items" -> documents.pageDetails.totalLength
    )
  }
}
