package controllers.forms

import play.api.data.Form
import play.api.data.Forms._
import models.orm.DocumentSetUser
import models.orm.DocumentSetUserRoleType

object UserRoleForm {

  def apply(documentSetId: Long): Form[DocumentSetUser] = Form(
    mapping(
      "email" -> email,
      "role" -> nonEmptyText)
      ((email, role) => DocumentSetUser(documentSetId, email, new DocumentSetUserRoleType(role)))
      (dsu => Some(dsu.userEmail, dsu.role.value)))

}