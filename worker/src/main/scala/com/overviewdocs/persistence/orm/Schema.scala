package com.overviewdocs.persistence.orm

import com.overviewdocs.postgres.SquerylEntrypoint._
import com.overviewdocs.tree.orm._

object Schema extends org.squeryl.Schema {
  override def columnNameFromPropertyName (propertyName: String) = NamingConventionTransforms.snakify(propertyName) 
  override def tableNameFromClassName(className: String) = NamingConventionTransforms.snakify(className)

  val documentProcessingErrors = table[DocumentProcessingError]
  val documentSetCreationJobs = table[DocumentSetCreationJob]
  val pages = table[Page]
  val tags = table[Tag]
  val tempDocumentSetFiles = table[TempDocumentSetFile]
  val trees = table[Tree]
  val uploadedFiles = table[UploadedFile]

  on(trees)(t => declare(t.id is(primaryKey)))
}
