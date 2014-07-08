package models.orm

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm._

object Schema extends org.squeryl.Schema {
  override def columnNameFromPropertyName(propertyName: String) =
    NamingConventionTransforms.snakify(propertyName)

  override def tableNameFromClassName(className: String) =
    NamingConventionTransforms.snakify(className)

  val documentProcessingErrors = table[DocumentProcessingError]
  val documentSearchResults = table[DocumentSearchResult]
  val documentSetCreationJobs = table[DocumentSetCreationJob]
  val documentSets = table[DocumentSet]
  val documentSetUsers = table[DocumentSetUser]
  val documents = table[Document]
  val documentTags = table[DocumentTag]
  val fileGroups = table[FileGroup]
  val groupedFileUploads = table[GroupedFileUpload]
  val nodeDocuments = table[NodeDocument]
  val nodes = table[Node]
  val pages = table[Page]
  val searchResults = table[SearchResult]
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
