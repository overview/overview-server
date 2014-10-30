package org.overviewproject.models

case class Node(
  val id: Long,
  val rootId: Long,
  val parentId: Option[Long],
  val description: String,
  val cachedSize: Int,
  val isLeaf: Boolean
)
