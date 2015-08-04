package com.overviewdocs.persistence.orm

import com.overviewdocs.postgres.SquerylEntrypoint.compositeKey
import org.squeryl.dsl.CompositeKey2
import org.squeryl.KeyedEntity

case class DocumentSetCreationJobNode(
  documentSetCreationJobId: Long,
  nodeId: Long
) extends KeyedEntity[CompositeKey2[Long, Long]] {
  override def id = compositeKey(documentSetCreationJobId, nodeId)
}
