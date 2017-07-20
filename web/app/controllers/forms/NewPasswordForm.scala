package controllers.forms

import play.api.data.{Form,Forms}

object NewPasswordForm {
  def apply() : Form[String] = Form(Forms.single("email" -> Forms.email))
}
