package org.overviewproject.tree.orm

import org.overviewproject.postgres.SquerylEntrypoint.compositeKey
import org.squeryl.KeyedEntity
import org.squeryl.dsl.CompositeKey2

case class DocumentSearchResult(
  documentId: Long,
  searchResultId: Long
) extends KeyedEntity[CompositeKey2[Long, Long]] {
  override def id = compositeKey(documentId, searchResultId)
}

