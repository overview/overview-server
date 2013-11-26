package models.orm


import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm._

object TestSchema extends org.squeryl.Schema {
  override def columnNameFromPropertyName(propertyName: String) =
    NamingConventionTransforms.snakify(propertyName)

  override def tableNameFromClassName(className: String) =
    NamingConventionTransforms.snakify(className)

  val documentSets = table[DocumentSet]
  val documents = table[Document]
  val nodes = table[Node]
  val tags = table[Tag]
  val documentTags = table[DocumentTag]
  val nodeDocuments = table[NodeDocument]
  on(documents)(d => declare(d.id is (primaryKey)))
  on(nodes)(n => declare(n.id is (primaryKey)))

}
