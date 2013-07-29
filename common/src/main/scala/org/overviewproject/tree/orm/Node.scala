package org.overviewproject.tree.orm

import org.squeryl.KeyedEntity
import org.squeryl.PrimitiveTypeMode._

case class Node(
  val id: Long = 0L,
  val documentSetId: Long,
  val parentId: Option[Long],
  val description: String,
  val cachedSize: Int,
  val cachedDocumentIds: Array[Long],
  val isLeaf: Boolean) extends KeyedEntity[Long] {

  override def isPersisted(): Boolean = true // use Schema's insert() to insert
}
