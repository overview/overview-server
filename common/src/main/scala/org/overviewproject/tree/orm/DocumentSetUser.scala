package com.overviewdocs.tree.orm

import org.squeryl.KeyedEntity
import org.squeryl.dsl.CompositeKey2

import com.overviewdocs.postgres.PostgresqlEnum
import com.overviewdocs.postgres.SquerylEntrypoint.compositeKey
import com.overviewdocs.tree.Ownership

case class DocumentSetUser(
  documentSetId: Long, 
  userEmail: String, 
  role: Ownership.Value
  ) extends KeyedEntity[CompositeKey2[Long, String]]  with DocumentSetComponent {

  def this() = this(0L, "", Ownership.Owner)

  override def id = compositeKey(documentSetId, userEmail)
}

