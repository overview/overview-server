package views.json.Tag

import play.api.libs.json.{JsArray,JsValue,Json}

import com.overviewdocs.models.Tag

object index {
  def withDocsetCounts(tags: Seq[(Tag,Long)]) = {
    val objs: Seq[JsValue] = tags.map { case (tag: Tag, docsetCount: Long) => Json.obj(
      "id" -> tag.id,
      "name" -> tag.name,
      "color" -> s"#${tag.color}",
      "size" -> docsetCount
    )}

    JsArray(objs)
  }

  def withDocsetCountsAndTreeCounts(tags: Seq[(Tag,Long,Long)]) = {
    val objs: Seq[JsValue] = tags.map { case (tag: Tag, docsetCount: Long, treeCount: Long) => Json.obj(
      "id" -> tag.id,
      "name" -> tag.name,
      "color" -> s"#${tag.color}",
      "size" -> docsetCount,
      "sizeInTree" -> treeCount
    )}

    JsArray(objs)
  }
}
