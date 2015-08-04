package com.overviewdocs.tree.orm

import com.overviewdocs.postgres.SquerylEntrypoint.compositeKey
import org.squeryl.dsl.CompositeKey2
import org.squeryl.KeyedEntity


case class NodeDocument(
  nodeId: Long,
  documentId: Long) extends KeyedEntity[CompositeKey2[Long, Long]] {

  override def id = compositeKey(nodeId, documentId)
}
