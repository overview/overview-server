package models.orm

import java.util.UUID
import org.squeryl.{KeyedEntity,KeyedEntityDef}
import org.squeryl.dsl.CompositeKey2

import models.{Session,User}
import org.overviewproject.models.ApiToken
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm._

object Schema extends org.squeryl.Schema {
  override def columnNameFromPropertyName(propertyName: String) =
    NamingConventionTransforms.snakify(propertyName)

  override def tableNameFromClassName(className: String) =
    NamingConventionTransforms.snakify(className)

  implicit object UserKED extends KeyedEntityDef[User, Long] {
    override def getId(u: User) = u.id
    override def idPropertyName = "id"
    override def isPersisted(u: User) = u.id != 0L
  }

  implicit object SessionKED extends KeyedEntityDef[Session, UUID] {
    override def getId(s: Session) = s.id
    override def idPropertyName = "id"
    override def isPersisted(s: Session) = (s.createdAt != s.updatedAt) // ugly hack!
  }

  val documentProcessingErrors = table[DocumentProcessingError]
  val documentSetCreationJobs = table[DocumentSetCreationJob]
  val documentSets = table[DocumentSet]
  val documentSetUsers = table[DocumentSetUser]
  val documents = table[Document]
  val documentTags = table[DocumentTag]
  val nodeDocuments = table[NodeDocument]
  val nodes = table[Node]
  val tags = table[Tag]
  val uploadedFiles = table[UploadedFile]
  val uploads = table[Upload]
  val users = table[User]
  val sessions = table[Session]
  val files = table[File]
  val trees = table[Tree]
 
  on(documents)(d => declare(d.id is (primaryKey)))
  on(nodes)(n => declare(n.id is (primaryKey)))
  on(sessions)(s => declare(s.id is (primaryKey)))
  on(trees)(t => declare(t.id is (primaryKey)))
}
