package views.json.Tag

import play.api.libs.json.JsValue
import play.api.libs.json.Json

import org.overviewproject.tree.orm.Tag

object index {
  def apply(tags: Iterable[(Tag,Int)]) = {
    def tagToJsValue(tag: Tag, count: Int) : JsValue = {
      Json.obj(
        "id" -> tag.id,
        "name" -> tag.name,
        "color" -> ("#" + tag.color),
        "size" -> count
      )
    }

    Json.obj("tags" -> tags.map(Function.tupled(tagToJsValue)).toSeq)
  }
}
