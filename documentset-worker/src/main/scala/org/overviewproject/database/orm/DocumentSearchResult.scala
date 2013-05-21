package org.overviewproject.database.orm

import org.overviewproject.postgres.SquerylEntrypoint._
import org.squeryl.KeyedEntity
import org.squeryl.dsl.CompositeKey2

case class DocumentSearchResult(
  documentId: Long,
  searchResultId: Long
) extends KeyedEntity[CompositeKey2[Long, Long]] {
  override def id = compositeKey(documentId, searchResultId)
}

