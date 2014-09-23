package views.json.api.pagination.PageInfo

import play.api.libs.json.{JsValue,Json}

import models.pagination.PageInfo

object show {
  def apply(info: PageInfo): JsValue = Json.obj(
    "offset" -> info.offset,
    "limit" -> info.limit,
    "total" -> info.total
  )
}
