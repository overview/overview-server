package controllers.forms

import play.api.data.Form
import play.api.data.Forms._

import models.orm.Tag

object TagForm {
  private val colorFormat = "^#[0-9a-fA-F]{6}$"

  def apply(tag: Tag) : Form[Tag] = {
    Form(
      mapping(
        "name" -> nonEmptyText,
        "color" -> nonEmptyText.verifying("color.invalid_format", _.matches(colorFormat))
      )
      ((name, color) => tag.copy(name=name, color=Some(color.substring(1).toLowerCase())))
      (aTag => Some(aTag.name, "#" + aTag.color))
    )
  }

  def apply(documentSetId: Long) : Form[Tag] = {
    val baseTag = Tag(documentSetId=documentSetId, name="", color=Some("000000"))
    apply(baseTag)
  }
}
