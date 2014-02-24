package views.json.Tag

import play.api.libs.json.{JsValue,Json}

import org.overviewproject.tree.orm.Tag

object index {
  def apply(tags: Iterable[(Tag,Long)]) = {
    Json.obj(
      "tags" -> tags.map { case (tag: Tag, docsetCount: Long) => Json.obj(
        "id" -> tag.id,
        "name" -> tag.name,
        "color" -> s"#${tag.color}",
        "size" -> docsetCount
      )}
    )
  }

  def apply(tags: Iterable[(Tag,Long,Long)])(implicit _notUsed: DummyImplicit) = {
    Json.obj(
      "tags" -> tags.map { case (tag: Tag, docsetCount: Long, treeCount: Long) => Json.obj(
        "id" -> tag.id,
        "name" -> tag.name,
        "color" -> s"#${tag.color}",
        "size" -> docsetCount,
        "sizeInTree" -> treeCount
      )}
    )
  }
}
