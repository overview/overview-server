package org.overviewproject.persistence.orm

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm._


object Schema extends org.squeryl.Schema {
  override def columnNameFromPropertyName (propertyName: String) = NamingConventionTransforms.snakify(propertyName) 
  override def tableNameFromClassName(className: String) = NamingConventionTransforms.snakify(className)
  
  val documentProcessingErrors = table[DocumentProcessingError]
  val documentSetCreationJobs = table[DocumentSetCreationJob]
  val documentSets = table[DocumentSet]
  val documents = table[Document]
  val documentTags = table[DocumentTag]
  val fileGroups = table[FileGroup]
  val files = table[File]
  val groupedFileUploads = table[GroupedFileUpload]
  val groupedProcessedFiles = table[GroupedProcessedFile]
  val nodeDocuments = table[NodeDocument]
  val nodes = table[Node]
  val pages = table[Page]
  val tags = table[Tag]
  val tempDocumentSetFiles = table[TempDocumentSetFile]
  val trees = table[Tree]
  val uploadedFiles = table[UploadedFile]
  
  on(documents)(d => declare(d.id is(primaryKey)))
  on(nodes)(n => declare(n.id is(primaryKey)))
  on(trees)(t => declare(t.id is(primaryKey)))
}
