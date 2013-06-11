package models.orm.finders

import org.squeryl.Query
import scala.collection.mutable.Buffer
import scala.language.implicitConversions

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.Node
import models.orm.Schema

object NodeFinder extends Finder {
  class NodeFinderResult(query: Query[Node]) extends FinderResult(query) {
    /** @return This Node and its descendents.
      *
      * @param nLevelsOfChildren: how many levels of children to return.
      */
    def withDescendents(nLevelsOfChildren: Int) : NodeFinderResult = {
      var queries = Buffer[Query[Node]](query)

      for (i <- 0 to nLevelsOfChildren - 1) {
        queries += join(queries.last, Schema.nodes)((p, n) =>
          select(n)
          on(p.id === n.parentId)
        )
      }

      queries.reduce(_.union(_))
    }

    /** @return (Option[Long],Long) pairs of (parentId,id). */
    def toParentIdAndId : FinderResult[(Option[Long],Long)] = {
      from(query)(q =>
        select(q.parentId, q.id)
        orderBy(q.cachedSize.desc)
      )
    }
  }
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
    )
  }

  /** @return All Nodes with the given parent IDs. */
  def byParentIds(parentIds: Traversable[Long]) : NodeFinderResult = {
    Schema.nodes.where(_.parentId in parentIds)
  }

  /** @return All Nodes with the given ID in the given DocumentSet. */
  def byDocumentSetAndId(documentSet: Long, id: Long) : NodeFinderResult = {
    from(Schema.nodes)(n =>
      where(n.documentSetId === documentSet and n.id === id)
      select(n)
    )
  }
}
