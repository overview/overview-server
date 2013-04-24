package models.orm

import org.overviewproject.postgres.SquerylEntrypoint.compositeKey
import org.squeryl.dsl.CompositeKey2
import org.squeryl.KeyedEntity

case class NodeDocument(
  nodeId: Long,
  documentId: Long) extends KeyedEntity[CompositeKey2[Long, Long]] {

  override def id = compositeKey(nodeId, documentId)
}
