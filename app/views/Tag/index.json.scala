package views.json.Tag

import play.api.libs.json.{JsArray,JsValue,Json}

import org.overviewproject.tree.orm.Tag

object index {
  def apply(tags: Iterable[(Tag,Long)]) = {
    val objs = tags.toSeq.map { case (tag: Tag, docsetCount: Long) => Json.obj(
      "id" -> tag.id,
      "name" -> tag.name,
      "color" -> s"#${tag.color}",
      "size" -> docsetCount
    )}

    JsArray(objs)
  }

  def apply(tags: Iterable[(Tag,Long,Long)])(implicit _notUsed: DummyImplicit) = {
    val objs = tags.toSeq.map { case (tag: Tag, docsetCount: Long, treeCount: Long) => Json.obj(
      "id" -> tag.id,
      "name" -> tag.name,
      "color" -> s"#${tag.color}",
      "size" -> docsetCount,
      "sizeInTree" -> treeCount
    )}

    JsArray(objs)
  }
}
