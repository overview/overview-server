package org.overviewproject.tree.orm

import org.squeryl.KeyedEntity

case class Page(
    data: Array[Byte],
    reference_count: Int,
    id: Long = 0L) extends KeyedEntity[Long] {
  
  override def isPersisted: Boolean = id != 0L
}
