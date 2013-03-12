package controllers.forms

import play.api.data.Form
import play.api.data.Forms
import models.PotentialUser
import models.UserRegistration
import models.util.PasswordTester
import models.UserRegistration
import models.UserRegistration


object UserForm {
  def apply(factory: (String, String, Boolean) => UserRegistration) : Form[UserRegistration] = {
    Form(
      Forms.mapping(
        "email" -> Forms.email,
        "password" -> Forms.nonEmptyText.verifying("password.secure", { (s: String) => (new PasswordTester(s)).isSecure }),
        "subscribe" -> Forms.boolean
      )(factory
      )(r => Some((r.user.email, r.user.password, r.subscribeToEmails)))
    )
  }

  def apply() : Form[UserRegistration] = apply((email: String, password: String, subscribe: Boolean) => UserRegistration(PotentialUser(email, password), subscribe))
}
