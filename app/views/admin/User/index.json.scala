package views.json.admin.User

import play.api.libs.json.{Json, JsValue}

import models.User
import models.pagination.Page

object index {
  def apply(page: Page[User]) : JsValue = {
    Json.obj(
      "page" -> (1 + (page.pageInfo.request.offset / page.pageInfo.request.limit)),
      "pageSize" -> page.pageInfo.request.limit,
      "total" -> page.pageInfo.total,
      "users" -> page.items.map(show(_))
    )
  }
}
