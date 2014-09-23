package views.json.api.DocumentInfo

import play.api.libs.json.{JsValue,Json}

import models.pagination.Page
import org.overviewproject.models.DocumentInfo

object index {
  def apply(page: Page[DocumentInfo]): JsValue = {
    val pagination = views.json.api.pagination.PageInfo.show(page.pageInfo)
    val items: Seq[JsValue] = page.items.map(show(_))
    Json.obj(
      "pagination" -> pagination,
      "items" -> Json.toJson(items)
    )
  }
}
