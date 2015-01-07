package views.json.Tag

import play.api.libs.json.JsValue

import org.overviewproject.models.Tag

object update {
  def apply(tag: Tag): JsValue = create(tag)
}
