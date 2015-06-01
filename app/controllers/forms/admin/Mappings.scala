package controllers.forms.admin

import play.api.data.{Forms, WrappedMapping}

import org.overviewproject.models.UserRole

object Mappings {
  val IsAdminUserRole = WrappedMapping(
    Forms.boolean,
    (b: Boolean) => if (b) UserRole.Administrator else UserRole.NormalUser,
    (r: UserRole.Value) => r == UserRole.Administrator
  )
}
