package org.overviewproject.models.tables

import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.DocumentSetUser

class DocumentSetUsersImpl(tag: Tag) extends Table[DocumentSetUser](tag, "document_set_user") {
  private implicit val roleColumnType = MappedColumnType.base[DocumentSetUser.Role,Int](
    _.isOwner match { case true => 1; case false => 2 },
    DocumentSetUser.Role.apply
  )

  def documentSetId = column[Long]("document_set_id")
  def userEmail = column[String]("user_email")
  def role = column[DocumentSetUser.Role]("role")

  def pk = primaryKey("document_set_user_pkey", (documentSetId, userEmail))
  def * = (documentSetId, userEmail, role) <> ((DocumentSetUser.apply _).tupled, DocumentSetUser.unapply)
}

object DocumentSetUsers extends TableQuery(new DocumentSetUsersImpl(_))
