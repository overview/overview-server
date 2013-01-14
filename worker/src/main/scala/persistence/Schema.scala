package persistence

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.{ Document, DocumentSetCreationJob, Node }

object Schema extends org.squeryl.Schema {
  override def columnNameFromPropertyName (propertyName: String) = NamingConventionTransforms.snakify(propertyName) 
  override def tableNameFromClassName(className: String) = NamingConventionTransforms.snakify(className)
  
  val nodes = table[Node]
  val documents = table[Document]
  val nodeDocuments = table[NodeDocument]
  val documentSetCreationJobs = table[DocumentSetCreationJob]
}