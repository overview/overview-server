package controllers.forms

import play.api.data.{Form,Forms}

import models.orm.{User,UserRole}

object AdminUserForm {
  def apply(user: User) : Form[User] = {
    Form(
      Forms.mapping(
        "email" -> Forms.email,
        "role" -> Forms.number.verifying("user.role.exists", { (roleId: Int) => UserRole.values.ids.contains(roleId) })
      )((email, role) => user.copy(email=email, role=UserRole(role))
      )(u => Some((u.email, u.role.id)))
    )
  }
}
