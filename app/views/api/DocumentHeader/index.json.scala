package views.json.api.DocumentHeader

import java.util.UUID
import play.api.libs.json.{JsValue,Json}

import models.pagination.Page
import org.overviewproject.models.DocumentHeader

object index {
  def apply(selectionId: UUID, page: Page[DocumentHeader], fields: Set[String]): JsValue = {
    val pagination = views.json.api.pagination.PageInfo.show(page.pageInfo)
    val items: Seq[JsValue] = page.items.map(show(_, fields))
    Json.obj(
      "selectionId" -> selectionId.toString,
      "pagination" -> pagination,
      "items" -> Json.toJson(items)
    )
  }
}
