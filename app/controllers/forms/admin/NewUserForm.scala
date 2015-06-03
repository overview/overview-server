package controllers.forms.admin

import com.github.t3hnar.bcrypt._
import java.util.Date
import java.sql.Timestamp
import play.api.data.{Form, Forms}

import models.OverviewUser
import models.User

object NewUserForm {
  def apply() : Form[User.CreateAttributes] = Form(
    Forms.mapping(
      "email" -> Forms.email,
      "password" -> Forms.text
    )((email: String, password: String) => User.CreateAttributes(
      email=email,
      passwordHash=password.bcrypt(OverviewUser.BcryptRounds),
      confirmedAt=Some(new Timestamp(new Date().getTime()))
    )
    )(attrs => Some(attrs.email, ""))
  )
}
