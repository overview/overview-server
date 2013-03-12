package controllers.forms

import play.api.data.Form
import play.api.data.Forms
import models.PotentialUser
import models.util.PasswordTester
import models.PotentialNewUser


object UserForm {
  def apply(factory: (String, String, Boolean) => PotentialNewUser) : Form[PotentialNewUser] = {
    Form(
      Forms.mapping(
        "email" -> Forms.email,
        "password" -> Forms.nonEmptyText.verifying("password.secure", { (s: String) => (new PasswordTester(s)).isSecure }),
        "subscribe" -> Forms.boolean
      )(factory
      )(u => Some((u.email, u.password, u.emailSubscriber)))
    )
  }

  def apply() : Form[PotentialNewUser] = apply((email: String, password: String, subscribe: Boolean) => PotentialNewUser(email, password, subscribe))
}
