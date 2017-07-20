package controllers.forms

import play.api.data.{Form,Forms}

object EditPasswordForm {
  def apply() : Form[String] = Form(Forms.single(
    "password" -> Forms.text.verifying(validation.minLengthPassword(7))
  ))
}
