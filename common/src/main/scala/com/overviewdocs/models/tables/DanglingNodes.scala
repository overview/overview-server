package com.overviewdocs.models.tables

import com.overviewdocs.database.Slick.api._
import com.overviewdocs.models.DanglingNode

class DanglingNodesImpl(tag: Tag) extends Table[DanglingNode](tag, "dangling_node") {
  def rootNodeId = column[Long]("root_node_id")

  def * = (rootNodeId) <> (DanglingNode.apply, DanglingNode.unapply)
}

object DanglingNodes extends TableQuery(new DanglingNodesImpl(_))
