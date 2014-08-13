package controllers.forms.admin

import com.github.t3hnar.bcrypt._
import play.api.data.{Form, Forms}

import models.OverviewUser
import models.{User, UserRole}

object EditUserForm {
  def apply(user: User) : Form[User] = Form(
    Forms.mapping(
      "is_admin" -> Forms.optional(Mappings.IsAdminUserRole),
      "password" -> Forms.optional(Forms.text)
    )((roleOpt, passwordOpt) => user.copy(
      role=roleOpt.getOrElse(user.role),
      passwordHash=passwordOpt.map(_.bcrypt(OverviewUser.BcryptRounds)).getOrElse(user.passwordHash)
    ))((user) => Some(Some(user.role), Some("")))
  )
}
