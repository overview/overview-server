package controllers.forms

import play.api.data.Form
import play.api.data.Forms._

object TagForm {
  private val colorFormat = "^#[0-9a-fA-F]{6}$"
  
  def apply(): Form[(String, String)] = 
    Form(
      tuple(
	"name" -> nonEmptyText,
	"color" -> nonEmptyText.verifying("color.invalid_format", { c => c.matches(colorFormat)})
	)
      )
}
