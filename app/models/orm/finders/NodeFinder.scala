package models.orm.finders

import scala.language.implicitConversions
import scala.language.postfixOps

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.Node
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }

import org.squeryl.Query

import models.orm.Schema

object NodeFinder extends Finder {
  class NodeFinderResult(query: Query[Node]) extends FinderResult(query)
  implicit private def queryToNodeFinderResult(query: Query[Node]) : NodeFinderResult = new NodeFinderResult(query)


  /** @return All Nodes in the given tree with the specified parent. */
  def byTreeAndParent(treeId: Long, parentId: Option[Long]): NodeFinderResult = {
    from(Schema.trees, Schema.nodes)((t, n) =>
      where(t.id === treeId and t.rootNodeId === n.rootId and n.parentId === parentId)
      select(n)
      orderBy(n.cachedSize desc)
    )
  }

  /** @return All Nodes with the given parent IDs. */
  def byParentIds(parentIds: Traversable[Long]) : NodeFinderResult = {
    from(Schema.nodes)(n =>
      where(n.parentId in parentIds)
      select(n)
      orderBy(n.cachedSize desc)
    )
  }
  
  /** @return All Nodes with the given ID in the given Tree */
  def byTreeAndId(treeId: Long, id: Long): NodeFinderResult = {
    from(Schema.trees, Schema.nodes)((t, n) =>
      where(t.id === treeId and t.rootNodeId === n.rootId and n.id === id)
      select(n)
    )
  }
}
