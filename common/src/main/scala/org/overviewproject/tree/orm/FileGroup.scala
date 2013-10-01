package org.overviewproject.tree.orm

import org.squeryl.KeyedEntity
import org.overviewproject.tree.orm.FileJobState.Complete

case class FileGroup(
    name: String,
    userEmail: String,
    state: FileJobState.Value,
    override val id: Long = 0L) extends KeyedEntity[Long] {

  def this() = this("", "", Complete)

  override def isPersisted(): Boolean = (id > 0)
}