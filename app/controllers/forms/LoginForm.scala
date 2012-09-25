package controllers.forms

import play.api.data.Form
import play.api.data.Forms

import models.PotentialUser

object LoginForm {
  def apply() : Form[PotentialUser] = {
    Form {
      Forms.mapping(
        "email" -> Forms.email,
        "password" -> Forms.text
      )(PotentialUser
      )(u => Some((u.email, u.password)))
      .verifying("forms.LoginForm.error.invalid_credentials", u => u.withValidCredentials.isDefined)
      .verifying("forms.LoginForm.error.not_confirmed", u => 
        !(u.withConfirmationRequest.isDefined && u.withValidCredentials.isDefined))
    }
  }
}
