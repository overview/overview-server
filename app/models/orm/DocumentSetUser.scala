package models.orm

import org.squeryl.KeyedEntity
import org.squeryl.dsl.CompositeKey2
import org.overviewproject.postgres.PostgresqlEnum
import org.overviewproject.postgres.SquerylEntrypoint.compositeKey

class DocumentSetUserRoleType(v: String) extends PostgresqlEnum(v, "document_set_user_role_type")

object DocumentSetUserRoleType {
  val Owner = new DocumentSetUserRoleType("Owner")
  val Viewer = new DocumentSetUserRoleType("Viewer")
} 

import DocumentSetUserRoleType._

case class DocumentSetUser(
    documentSetId: Long, 
    userEmail: String, 
    role: DocumentSetUserRoleType = Owner) extends KeyedEntity[CompositeKey2[Long, String]] {
  
  override def id = compositeKey(documentSetId, userEmail)
}

