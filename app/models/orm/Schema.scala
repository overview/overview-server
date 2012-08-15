package models.orm

import org.squeryl.PrimitiveTypeMode._

object Schema extends org.squeryl.Schema {
  val users = table[User]("user")
  val documentSets = table[DocumentSet]("document_set")
  val documentSetUsers = manyToManyRelation(documentSets, users, "document_set_user").via[DocumentSetUser]((ds, u, dsu) => (dsu.documentSetId === ds.id, dsu.userId === u.id))
}
