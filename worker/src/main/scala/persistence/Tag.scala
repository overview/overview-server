package persistence

import org.squeryl.KeyedEntity

case class Tag(
  documentSetId: Long,
  name: String,
  color: Option[String] = None,    
  id: Long = 0l) extends KeyedEntity[Long] {

  override def isPersisted(): Boolean = (id > 0)
}