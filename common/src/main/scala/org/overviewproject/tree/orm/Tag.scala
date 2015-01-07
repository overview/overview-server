package org.overviewproject.tree.orm

import org.squeryl.KeyedEntity

import org.overviewproject.models.{Tag => BetterTag}

case class Tag(
  documentSetId: Long,
  name: String,
  color: String,
  id: Long = 0L) extends KeyedEntity[Long] with DocumentSetComponent {

  override def isPersisted(): Boolean = id != 0L

  def toTag: BetterTag = BetterTag(id, documentSetId, name, color)
}
