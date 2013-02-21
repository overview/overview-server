package models.orm

import org.overviewproject.postgres.PostgresqlEnum

class DocumentSetUserRoleType(v: String) extends PostgresqlEnum(v, "document_set_user_role_type")

object DocumentSetUserRoleType {
  val Owner = new DocumentSetUserRoleType("Owner")
  val Viewer = new DocumentSetUserRoleType("Viewer")
}

import DocumentSetUserRoleType._

case class DocumentSetUser(documentSetId: Long, userEmail: String, role: DocumentSetUserRoleType = Owner) 

