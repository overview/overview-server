package controllers.forms

import play.api.data.Form
import play.api.data.Forms._

import com.overviewdocs.tree.Ownership
import com.overviewdocs.tree.orm.DocumentSetUser

object DocumentSetUserForm {
  def apply(documentSetId: Long): Form[DocumentSetUser] = Form(
    mapping(
      "email" -> email,
      "role" -> nonEmptyText.verifying("role.invalid_role", { r => Ownership.values.find(_.toString == r).isDefined }))
      ((email, role) => DocumentSetUser(documentSetId, email, Ownership.withName(role)))
      (dsu => Some(dsu.userEmail, dsu.role.toString)
    )
  )
}
