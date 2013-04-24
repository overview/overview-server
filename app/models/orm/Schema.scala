package models.orm

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm._

object Schema extends org.squeryl.Schema {
  override def columnNameFromPropertyName(propertyName: String) =
    NamingConventionTransforms.snakify(propertyName)

  override def tableNameFromClassName(className: String) =
    NamingConventionTransforms.snakify(className)

  val documents = table[Document]
  val documentSets = table[DocumentSet]
  val documentSetCreationJobs = table[DocumentSetCreationJob]
  val nodes = table[Node]
  val nodeDocuments = table[NodeDocument]
  val logEntries = table[LogEntry]
  val users = table[User]
  val tags = table[Tag]
  val documentTags = table[DocumentTag]
  val uploads = table[Upload]
  val uploadedFiles = table[UploadedFile]
  val documentProcessingErrors = table[DocumentProcessingError]
  val documentSetUsers = table[DocumentSetUser]

  on(documents)(d => declare(d.id is(primaryKey)))
  on(nodes)(n => declare(n.id is(primaryKey)))
}
