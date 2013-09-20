package org.overviewproject.tree.orm

import org.squeryl.KeyedEntity
import java.util.UUID
import java.sql.Timestamp
import org.overviewproject.tree.orm.FileJobState._

case class File (
  fileGroupId: Long,
  guid: UUID,
  name: String,
  contentType: String,
  size: Long,
  state: FileJobState,
  text: String,
  uploadedAt: Timestamp,
  override val id: Long = 0L   
) extends KeyedEntity[Long] {
  override def isPersisted(): Boolean = (id > 0)
  
  def this() = this(0, new UUID(0, 0), "", "", 0, Error, "", new Timestamp(0))
}