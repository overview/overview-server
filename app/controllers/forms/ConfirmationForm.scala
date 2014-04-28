package controllers.forms

import play.api.data.{Form,Forms}

import models.OverviewUser

object ConfirmationForm {
  def apply() : Form[OverviewUser with models.ConfirmationRequest] = {
    Form(
      Forms.mapping(
        "token" -> Forms.nonEmptyText.verifying("forms.Confirmation.token.not_found", OverviewUser.findByConfirmationToken(_).isDefined)
      )(OverviewUser.findByConfirmationToken(_).getOrElse(throw new Exception("User already confirmed"))
      )(u => Some(u.confirmationToken))
    )
  }
}
