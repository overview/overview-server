package controllers.forms

import play.api.data.{Form,Forms}

import models.PotentialExistingUser

object LoginForm {
  def apply() : Form[PotentialExistingUser] = {
    Form(
      Forms.mapping(
        "email" -> Forms.email,
        "password" -> Forms.text
      )(PotentialExistingUser.apply)(PotentialExistingUser.unapply)
    )
  }
}
