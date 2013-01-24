package org.overviewproject.tree.orm

import org.overviewproject.postgres.SquerylEntrypoint._

object Schema extends org.squeryl.Schema {
  override def columnNameFromPropertyName(propertyName: String) = NamingConventionTransforms.snakify(propertyName)

  override def tableNameFromClassName(className: String) = NamingConventionTransforms.snakify(className)

  val nodes = table[Node]
  val documents = table[Document]
  val documentProcessingErrors = table[DocumentProcessingError]
  val documentSetCreationJobs = table[DocumentSetCreationJob]
}