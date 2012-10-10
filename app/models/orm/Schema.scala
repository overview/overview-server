package models.orm

import org.squeryl.PrimitiveTypeMode._

object Schema extends org.squeryl.Schema {
  val documents = table[Document]("document")
  val documentSets = table[DocumentSet]("document_set")
  val documentSetCreationJobs = table[DocumentSetCreationJob]("document_set_creation_job")
  val nodes = table[Node]("node")
  val logEntries = table[LogEntry]("log_entry")
  val users = table[User]("user")
  val tags = table[Tag]("tag")
  val documentTags = table[DocumentTag]("document_tag")
  
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

  val documentSetUsers =
    manyToManyRelation(documentSets, users, "document_set_user").
      via[DocumentSetUser]((ds, u, dsu) =>
        (dsu.documentSetId === ds.id, dsu.userId === u.id))

  val userLogEntries =
    oneToManyRelation(users, logEntries).
      via((u, le) => u.id === le.userId)
}
