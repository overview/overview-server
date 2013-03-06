package org.overviewproject.persistence.orm

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm._


object Schema extends org.squeryl.Schema {
  override def columnNameFromPropertyName (propertyName: String) = NamingConventionTransforms.snakify(propertyName) 
  override def tableNameFromClassName(className: String) = NamingConventionTransforms.snakify(className)
  
  val nodes = table[Node]
  val documents = table[Document]
  val nodeDocuments = table[NodeDocument]
  val documentSetCreationJobs = table[DocumentSetCreationJob]
  val documentProcessingErrors = table[DocumentProcessingError]
  val uploadedFiles = table[UploadedFile]
  val tags = table[Tag]
  val documentTags = table[DocumentTag]
  val documentSets = table[DocumentSet]
  
  on(documents)(d => declare(d.id is(primaryKey)))
}