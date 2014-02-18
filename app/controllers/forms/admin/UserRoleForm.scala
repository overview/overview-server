package controllers.forms.admin

import play.api.data.{Form, Forms}

import models.orm.{User, UserRole}

object UserRoleForm {
  def apply(user: User) : Form[User] = Form(
    Forms.mapping(
      "is_admin" -> Mappings.IsAdminUserRole
    )((role) => user.copy(role=role)
    )((user) => Some(user.role))
  )
}
