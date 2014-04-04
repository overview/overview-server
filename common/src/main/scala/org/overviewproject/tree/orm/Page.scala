package org.overviewproject.tree.orm

import org.squeryl.KeyedEntity

case class Page(
    fileId: Long,
    pageNumber: Int,
    data: Array[Byte],
    referenceCount: Int,
    id: Long = 0L) extends KeyedEntity[Long] {
  
  override def isPersisted: Boolean = id != 0L
}
