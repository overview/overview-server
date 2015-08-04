package models.orm

import java.util.UUID
import org.squeryl.{KeyedEntity,KeyedEntityDef}
import org.squeryl.dsl.CompositeKey2

import models.{Session,User}
import com.overviewdocs.models.ApiToken
import com.overviewdocs.postgres.SquerylEntrypoint._
import com.overviewdocs.tree.orm._

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

  val documentProcessingErrors = table[DocumentProcessingError]
  val documentSetCreationJobs = table[DocumentSetCreationJob]
  val documentSetUsers = table[DocumentSetUser]
  val documentTags = table[DocumentTag]
  val nodeDocuments = table[NodeDocument]
  val nodes = table[Node]
  val tags = table[Tag]
  val users = table[User]
  val trees = table[Tree]
 
  on(nodes)(n => declare(n.id is (primaryKey)))
  on(trees)(t => declare(t.id is (primaryKey)))
}
