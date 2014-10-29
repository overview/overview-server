package org.overviewproject.models

case class DocumentSetUser(
  documentSetId: Long,
  userEmail: String,
  role: DocumentSetUser.Role
)

object DocumentSetUser {
  class Role(val isOwner: Boolean)
  object Role {
    def apply(i: Int) = i match {
      case 2 => new Role(true)
      case _ => new Role(false)
    }
  }
}
