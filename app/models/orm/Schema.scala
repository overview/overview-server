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
  
  val documentSetDocuments =
    oneToManyRelation(documentSets, documents).
      via((ds, d) => ds.id === d.documentSetId)

  val documentSetDocumentSetCreationJobs =
    oneToManyRelation(documentSets, documentSetCreationJobs).
      via((ds, dscj) => ds.id === dscj.documentSetId)

  val documentSetLogEntries =
    oneToManyRelation(documentSets, logEntries).
      via((ds, le) => ds.id === le.documentSetId)

  val documentSetNodes =
    oneToManyRelation(documentSets, nodes).
      via((ds, n) => ds.id === n.documentSetId)

  val uploadedFileDocumentSets =
    oneToManyRelation(uploadedFiles, documentSets).
      via((uf, ds) => uf.id === ds.uploadedFileId)

  val userLogEntries =
    oneToManyRelation(users, logEntries).
      via((u, le) => u.id === le.userId)
}
