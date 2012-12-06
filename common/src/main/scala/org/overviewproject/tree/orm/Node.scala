package org.overviewproject.tree.orm

import org.squeryl.annotations.Column
import org.squeryl.KeyedEntity

class Node(
  @Column("document_set_id") val documentSetId: Long,
  @Column("parent_id") val parentId: Option[Long],
  val description: String,
  @Column("cached_size") val cachedSize: Int,
  @Column("cached_document_ids") val cachedDocumentIds: Array[Long]) extends KeyedEntity[Long] {
  
  val id: Long = 0l

}