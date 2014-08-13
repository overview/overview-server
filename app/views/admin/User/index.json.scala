package views.json.admin.User

import play.api.libs.json.{Json, JsValue}

import models.User
import org.overviewproject.tree.orm.finders.ResultPage

object index {
  def apply(users: ResultPage[User]) : JsValue = {
    Json.obj(
      "page" -> users.pageDetails.pageNum,
      "pageSize" -> users.pageDetails.pageSize,
      "total" -> users.pageDetails.totalLength,
      "users" -> users.map(show(_))
    )
  }
}
