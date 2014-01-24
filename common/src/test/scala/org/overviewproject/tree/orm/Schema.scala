package org.overviewproject.tree.orm

import org.overviewproject.postgres.SquerylEntrypoint._

object Schema extends org.squeryl.Schema {
  override def columnNameFromPropertyName(propertyName: String) = NamingConventionTransforms.snakify(propertyName)

  override def tableNameFromClassName(className: String) = NamingConventionTransforms.snakify(className)

  val documentSets = table[DocumentSet]
  val nodes = table[Node]
  val documents = table[Document]
  val documentProcessingErrors = table[DocumentProcessingError]
  val documentSetCreationJobs = table[DocumentSetCreationJob]
  val trees = table[Tree]
  
  on(documents)(d => declare(d.id is(primaryKey)))
  on(nodes)(n => declare(n.id is(primaryKey)))
  on(trees)(t => declare(t.id is(primaryKey)))  
}