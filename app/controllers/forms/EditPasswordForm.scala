package controllers.forms

import play.api.data.{Form,Forms}

import models.util.PasswordTester

object EditPasswordForm {
  def apply() : Form[String] = Form(Forms.single(
    "password" -> Forms.nonEmptyText.verifying("password.secure", { (s: String) => (new PasswordTester(s)).isSecure })
  ))
}
