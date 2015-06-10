package org.overviewproject.models.tables

import org.overviewproject.database.Slick.api._
import org.overviewproject.models.DocumentSetUser

class DocumentSetUsersImpl(tag: Tag) extends Table[DocumentSetUser](tag, "document_set_user") {
  def documentSetId = column[Long]("document_set_id")
  def userEmail = column[String]("user_email")
  def role = column[DocumentSetUser.Role]("role")(documentSetUserRoleColumnType)

  def pk = primaryKey("document_set_user_pkey", (documentSetId, userEmail))
  def * = (documentSetId, userEmail, role) <> ((DocumentSetUser.apply _).tupled, DocumentSetUser.unapply)
}

object DocumentSetUsers extends TableQuery(new DocumentSetUsersImpl(_))
