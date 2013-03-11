package org.overviewproject.tree.orm

import org.squeryl.annotations.Column
import org.squeryl.KeyedEntity
import org.squeryl.PrimitiveTypeMode._

case class Node(
  @Column("document_set_id") val documentSetId: Long,
  @Column("parent_id") val parentId: Option[Long],
  val description: String,
  @Column("cached_size") val cachedSize: Int,
  @Column("cached_document_ids") val cachedDocumentIds: Array[Long],
  id: Long = 0l) extends KeyedEntity[Long] {
  
  
  override def isPersisted(): Boolean = (id > 0)
}
