package org.overviewproject.tree.orm


import org.squeryl.KeyedEntity

case class FileGroup(
     userEmail: String,
     completed: Boolean,
     deleted: Boolean,
     apiToken: Option[String] = None,
     id: Long = 0l) extends KeyedEntity[Long] {

  override def isPersisted(): Boolean = (id > 0)
}

