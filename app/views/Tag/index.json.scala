package views.json.Tag

import play.api.libs.json.{JsArray,JsValue,Json}

import com.overviewdocs.models.Tag

object index {
  def withCounts(tags: Seq[(Tag,Int)]) = {
    val objs: Seq[JsValue] = tags.map { case (tag: Tag, count: Int) => Json.obj(
      "id" -> tag.id,
      "name" -> tag.name,
      "color" -> s"#${tag.color}",
      "size" -> count
    )}

    JsArray(objs)
  }
}
