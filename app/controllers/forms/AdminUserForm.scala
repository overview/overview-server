package controllers.forms

import play.api.data.{Form,Forms}

import models.orm.UserRole
import models.OverviewUser

object AdminUserForm {
  def apply(user: OverviewUser) : Form[OverviewUser] = {
    Form(
      Forms.mapping(
        "email" -> Forms.email,
        "role" -> Forms.number.verifying("user.role.exists", { (roleId: Int) => UserRole.values.map(_.id).contains(roleId) })
      )((email, role) => {
        val withEmail = user.withEmail(email)
        if (role == UserRole.Administrator.id) {
          withEmail.asAdministrator
        } else {
          withEmail.asNormalUser
        }
      }
      )(u => Some((u.email, if (u.isAdministrator) UserRole.Administrator.id else UserRole.NormalUser.id)))
    )
  }
}
