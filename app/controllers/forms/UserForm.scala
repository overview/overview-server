package controllers.forms

import play.api.data.Form
import play.api.data.Forms

import models.PotentialUser
import models.util.PasswordTester

object UserForm {
  def apply(factory: (String, String, Boolean) => (PotentialUser, Boolean)) : Form[(PotentialUser, Boolean)] = {
    Form(
      Forms.mapping(
        "email" -> Forms.email,
        "password" -> Forms.nonEmptyText.verifying("password.secure", { (s: String) => (new PasswordTester(s)).isSecure }),
        "subscribe" -> Forms.boolean
      )(factory
      )(r => Some((r._1.email, r._1.password, r._2)))
    )
  }

  def apply() : Form[(PotentialUser, Boolean)] = apply((email: String, password: String, subscribe: Boolean) => (PotentialUser(email, password), subscribe))
}
