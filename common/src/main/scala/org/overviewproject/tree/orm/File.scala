package org.overviewproject.tree.orm

import org.squeryl.KeyedEntity
import java.util.UUID
import java.sql.Timestamp
import org.overviewproject.tree.orm.FileJobState.Error

case class File (
  guid: UUID,
  name: String,
  contentType: String,
  contentsOid: Long,
  size: Long,
  state: FileJobState.Value,
  text: String,
  uploadedAt: Timestamp,
  override val id: Long = 0L   
) extends KeyedEntity[Long] {
  override def isPersisted(): Boolean = (id > 0)
  
  def this() = this(new UUID(0, 0), "", "", 0, 0, Error, "", new Timestamp(0))
}