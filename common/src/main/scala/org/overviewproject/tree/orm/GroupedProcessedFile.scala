package org.overviewproject.tree.orm

import org.squeryl.KeyedEntity

case class GroupedProcessedFile (
  fileGroupId: Long,
  contentType: String,
  name: String,
  errorMessage: Option[String],
  text: Option[String],
  contentsOid: Long,
  size: Long,
  override val id: Long = 0L   
) extends KeyedEntity[Long] {
  override def isPersisted(): Boolean = (id > 0)

}