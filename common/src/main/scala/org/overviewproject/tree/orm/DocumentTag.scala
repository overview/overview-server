package org.overviewproject.tree.orm

import org.overviewproject.postgres.SquerylEntrypoint.compositeKey
import org.squeryl.dsl.CompositeKey2
import org.squeryl.KeyedEntity

case class DocumentTag(
  documentId: Long,
  tagId: Long) extends KeyedEntity[CompositeKey2[Long, Long]] {
  override def id = compositeKey(documentId, tagId)
}
