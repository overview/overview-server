package views.json.api.DocumentHeader

import play.api.libs.json.{JsValue,Json}

import models.pagination.Page
import org.overviewproject.models.DocumentHeader

object index {
  def apply(page: Page[DocumentHeader], fields: Set[String]): JsValue = {
    val pagination = views.json.api.pagination.PageInfo.show(page.pageInfo)
    val items: Seq[JsValue] = page.items.map(show(_, fields))
    Json.obj(
      "pagination" -> pagination,
      "items" -> Json.toJson(items)
    )
  }
}
