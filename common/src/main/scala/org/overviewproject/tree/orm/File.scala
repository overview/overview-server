package org.overviewproject.tree.orm

import org.squeryl.KeyedEntity
import java.util.UUID

case class File (
  guid: UUID,
  name: String,
  contentType: String,
  contentsOid: Long,
  size: Long,
  override val id: Long = 0L   
) extends KeyedEntity[Long] {
  override def isPersisted(): Boolean = (id > 0)
}