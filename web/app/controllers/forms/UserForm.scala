package controllers.forms

import play.api.data.{Form,Forms}

import models.PotentialNewUser

object UserForm {
  def apply(): Form[PotentialNewUser] = {
    Form(
      Forms.mapping(
        "email" -> Forms.email,
        "password" -> Forms.text.verifying(validation.minLengthPassword(7)),
        "subscribe" -> Forms.boolean
      )(PotentialNewUser.apply)(PotentialNewUser.unapply)
    )
  }
}
