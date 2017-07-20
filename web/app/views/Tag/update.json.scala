package views.json.Tag

import play.api.libs.json.JsValue

import com.overviewdocs.models.Tag

object update {
  def apply(tag: Tag): JsValue = create(tag)
}
