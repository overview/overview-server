package controllers.forms

import play.api.data.Form
import play.api.data.Forms._

object TagForm {

  def apply(): Form[(String, String)] = 
    Form(
      tuple(
	"name" -> nonEmptyText,
	"color" -> nonEmptyText
	)
      )
}
