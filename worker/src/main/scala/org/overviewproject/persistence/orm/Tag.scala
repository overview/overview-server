package org.overviewproject.persistence.orm

import org.squeryl.KeyedEntity

case class Tag(
  documentSetId: Long,
  name: String,
  color: String,    
  id: Long = 0l) extends KeyedEntity[Long] {

  override def isPersisted(): Boolean = (id > 0)
}
