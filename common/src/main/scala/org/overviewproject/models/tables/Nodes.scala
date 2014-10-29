package org.overviewproject.models.tables

import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.Node

class NodesImpl(tag: Tag) extends Table[Node](tag, "node") {
  def id = column[Long]("id", O.PrimaryKey)
  def rootId = column[Long]("root_id")
  def parentId = column[Option[Long]]("parent_id")
  def description = column[String]("description")
  def cachedSize = column[Int]("cached_size")
  def isLeaf = column[Boolean]("is_leaf")

  def * = (id, rootId, parentId, description, cachedSize, isLeaf) <> ((Node.apply _).tupled, Node.unapply)
}

object Nodes extends TableQuery(new NodesImpl(_))
