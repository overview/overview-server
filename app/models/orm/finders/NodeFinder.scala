package models.orm.finders

import org.squeryl.Query
import scala.collection.mutable.Buffer
import scala.language.implicitConversions
import scala.language.postfixOps

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.Node
import models.orm.Schema

object NodeFinder extends Finder {
  class NodeFinderResult(query: Query[Node]) extends FinderResult(query)
  implicit private def queryToNodeFinderResult(query: Query[Node]) : NodeFinderResult = new NodeFinderResult(query)

  /** @return All Nodes in a DocumentSet. */
  def byDocumentSet(documentSet: Long) : NodeFinderResult = {
    from(Schema.nodes)(n =>
      where(n.documentSetId === documentSet)
      select(n)
    )
  }

  /** @return All Nodes with the given DocumentSet and parent. */
  def byDocumentSetAndParent(documentSet: Long, parentId: Option[Long]) : NodeFinderResult = {
    from(Schema.nodes)(n =>
      where(n.documentSetId === documentSet and n.parentId === parentId)
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

  /** @return All Nodes with the given ID in the given DocumentSet. */
  def byDocumentSetAndId(documentSet: Long, id: Long) : NodeFinderResult = {
    from(Schema.nodes)(n =>
      where(n.documentSetId === documentSet and n.id === id)
      select(n)
    )
  }
}
