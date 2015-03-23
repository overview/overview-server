package org.overviewproject.models

case class DocumentSetUser(
  documentSetId: Long,
  userEmail: String,
  role: DocumentSetUser.Role
)

object DocumentSetUser {
  case class Role(val isOwner: Boolean)
  object Role {
    def apply(i: Int): Role = Role(i == 1) // database int to role
  }
}
