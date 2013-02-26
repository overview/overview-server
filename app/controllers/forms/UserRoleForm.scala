package controllers.forms

import play.api.data.Form
import play.api.data.Forms._
import models.orm.DocumentSetUser
import models.orm.DocumentSetUserRoleType

object UserRoleForm {
  private val RoleFormat = "^(Owner|Viewer)$"
    
  def apply(documentSetId: Long): Form[DocumentSetUser] = Form(
    mapping(
      "email" -> email,
      "role" -> nonEmptyText.verifying("role.invalid_role", { r =>  r matches(RoleFormat) }))
      ((email, role) => DocumentSetUser(documentSetId, email, new DocumentSetUserRoleType(role)))
      (dsu => Some(dsu.userEmail, dsu.role.value)))

}