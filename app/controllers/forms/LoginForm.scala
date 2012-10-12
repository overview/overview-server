package controllers.forms

import play.api.data.Form
import play.api.data.Forms

import models.{OverviewUser,PotentialUser}

object LoginForm {
  def apply() : Form[OverviewUser] = {
    Form(
      Forms.mapping(
        "email" -> Forms.email,
        "password" -> Forms.text
      )(PotentialUser
      )(u => Some((u.email, u.password)))
      .transform[Option[OverviewUser]](_.withValidCredentials, _.map(u => PotentialUser(u.email, "")).get) // we can't reverse the password ... but really, we don't need to
      .verifying("forms.LoginForm.error.invalid_credentials", u => u.isDefined)
      .transform[OverviewUser](_.get, Option(_))
      .verifying("forms.LoginForm.error.not_confirmed", u => !u.withConfirmationRequest.isDefined)
    )
  }
}
