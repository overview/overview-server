package controllers.forms

import models.OverviewTag
import models.TagColor
import play.api.data.Form
import play.api.data.Forms._

object TagForm {
  private val colorFormat = "^#[0-9a-fA-F]{6}$"
  
  def apply(tag: OverviewTag): Form[OverviewTag with TagColor] = 
    Form(
      mapping(
        "name" -> nonEmptyText,
        "color" -> nonEmptyText.verifying("color.invalid_format", { c => c.matches(colorFormat)})
      )
      ((name, color) => tag.withName(name).withColor(color.substring(1).toLowerCase()))
      (coloredTag => Some(coloredTag.name, "#" + coloredTag.color))
    )
}
