package org.overviewproject.database.orm

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm._


object Schema extends org.squeryl.Schema {
  override def columnNameFromPropertyName (propertyName: String) = NamingConventionTransforms.snakify(propertyName) 
  override def tableNameFromClassName(className: String) = NamingConventionTransforms.snakify(className)

  val searchResults = table[SearchResult]
  val documentSearchResults = table[DocumentSearchResult]
  val documents = table[Document]
  val documentSets = table[DocumentSet]
  val groupedProcessedFiles = table[GroupedProcessedFile]
  val fileTexts = table[FileText]
  val groupedFileUploads = table[GroupedFileUpload]
  val fileGroups = table[FileGroup]
  val documentSetCreationJobs = table[DocumentSetCreationJob]
  val documentSetUsers = table[DocumentSetUser]
  val logEntries = table[LogEntry]
  val documentTags = table[DocumentTag]
  val tags = table[Tag]
  val nodeDocuments = table[NodeDocument]
  val nodes = table[Node]
  val documentProcessingErrors = table[DocumentProcessingError]
  val uploadedFiles = table[UploadedFile]
  val files = table[File]
  val trees = table[Tree]
  val pages = table[Page]
  val tempDocumentSetFiles = table[TempDocumentSetFile]
  
  on(documents)(d => declare(d.id is(primaryKey)))  
  on(nodes)(n => declare(n.id is(primaryKey)))
  on(trees)(t => declare(t.id is(primaryKey)))
}