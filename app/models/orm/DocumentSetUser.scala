package models.orm

import org.squeryl.KeyedEntity
import org.squeryl.dsl.CompositeKey2

import org.overviewproject.postgres.PostgresqlEnum
import org.overviewproject.postgres.SquerylEntrypoint.compositeKey
import org.overviewproject.tree.Ownership

case class DocumentSetUser(
  documentSetId: Long, 
  userEmail: String, 
  role: Ownership.Value
  ) extends KeyedEntity[CompositeKey2[Long, String]] {

  def this() = this(0L, "", Ownership.Owner)

  override def id = compositeKey(documentSetId, userEmail)
}

