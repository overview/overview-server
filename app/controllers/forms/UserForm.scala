package controllers.forms

import play.api.data.Form
import play.api.data.Forms

import models.PotentialUser
import models.util.PasswordTester

object UserForm {
  def apply(factory: (String, String) => PotentialUser) : Form[PotentialUser] = {
    Form(
      Forms.mapping(
        "email" -> Forms.email,
        "password" -> Forms.nonEmptyText.verifying("password.secure", { (s: String) => (new PasswordTester(s)).isSecure })
      )(factory
      )(u => Some(u.email, u.password))
    )
  }

  def apply() : Form[PotentialUser] = apply((email: String, password: String) => PotentialUser(email, password))
}
