package controllers.forms.admin

import com.github.t3hnar.bcrypt._
import java.util.Date
import java.sql.Timestamp
import play.api.data.{Form, Forms}

import models.OverviewUser
import models.orm.User

object NewUserForm {
  def apply() : Form[User] = Form(
    Forms.mapping(
      "email" -> Forms.email,
      "password" -> Forms.text
    )((email: String, password: String) => User(
      email=email,
      passwordHash=password.bcrypt(OverviewUser.BcryptRounds),
      confirmedAt=Some(new Timestamp(new Date().getTime()))
    )
    )(user => Some(user.email, ""))
  )
}
