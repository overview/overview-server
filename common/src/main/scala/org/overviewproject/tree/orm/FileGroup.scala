package org.overviewproject.tree.orm

import org.squeryl.KeyedEntity
import java.util.UUID
import org.overviewproject.tree.orm.FileJobState.Error

case class FileGroup(
    guid: UUID,
    userEmail: String,
    state: FileJobState.Value,
    override val id: Long = 0L) extends KeyedEntity[Long] {

  def this() = this(new UUID(0, 0), "", Error)

  override def isPersisted(): Boolean = (id > 0)
}