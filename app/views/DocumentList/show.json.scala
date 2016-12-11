package views.json.DocumentList

import java.util.UUID
import play.api.libs.json.{JsValue,Json}

import models.pagination.Page
import com.overviewdocs.models.DocumentHeader

object show {
  private def documentToJson(document: DocumentHeader, nodeIds: Seq[Long], tagIds: Seq[Long]) : JsValue = {
    Json.obj(
      "id" -> document.id,
      "documentSetId" -> document.documentSetId.toString,
      "description" -> document.keywords,
      "title" -> document.title,
      "page_number" -> document.pageNumber,
      "url" -> document.viewUrl,
      "nodeids" -> nodeIds,
      "tagids" -> tagIds,
      "thumbnailUrl" -> document.thumbnailLocation.map(_ => s"/documents/${document.id}.png")
    )
  }

  def apply(selectionId: UUID, documents: Page[(DocumentHeader,Seq[Long],Seq[Long])]) = {
    Json.obj(
      "selection_id" -> selectionId.toString,
      "total_items" -> documents.pageInfo.total,
      "documents" -> documents.items.map(Function.tupled(documentToJson)).toSeq
    )
  }
}
