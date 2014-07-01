package models.orm.finders

import scala.language.implicitConversions
import scala.language.postfixOps
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.finders.{ BaseNodeDocumentFinder, FinderResult }
import org.overviewproject.tree.orm.NodeDocument
import org.squeryl.Query
import org.squeryl.dsl.GroupWithMeasures


import models.orm.{ Schema }

object NodeDocumentFinder extends BaseNodeDocumentFinder(Schema.nodeDocuments, Schema.documents) {
  class NodeDocumentFinderResult(query: Query[NodeDocument]) extends FinderResult(query) {
    /** @return (nodeId, count) pairs. */
    def tagCountsByNodeId(tag: Long): FinderResult[(Long, Long)] = {
      val main: Query[GroupWithMeasures[Long, Long]] = join(query, Schema.documentTags)((nd, dt) =>
        where(dt.tagId === tag)
          groupBy (nd.nodeId)
          compute (org.overviewproject.postgres.SquerylEntrypoint.count)
          on (nd.documentId === dt.documentId))

      from(main)(row => select(row.key, row.measures))
    }

    /** @return (nodeId, tagId, count) tuples. */
    def allTagCountsByNodeId: FinderResult[(Long, Long, Long)] = {
      val main: Query[GroupWithMeasures[Product2[Long, Long], Long]] = join(query, Schema.documentTags)((nd, dt) =>
        groupBy(nd.nodeId, dt.tagId)
          compute (org.overviewproject.postgres.SquerylEntrypoint.count)
          on (nd.documentId === dt.documentId))

      from(main)(row => select(row.key._1, row.key._2, row.measures))
    }

    /** @return (nodeId, count) pairs. */
    def searchResultCountsByNodeId(searchResult: Long): FinderResult[(Long, Long)] = {
      val main: Query[GroupWithMeasures[Long, Long]] = join(query, Schema.documentSearchResults)((nd, ds) =>
        where(ds.searchResultId === searchResult)
          groupBy (nd.nodeId)
          compute (org.overviewproject.postgres.SquerylEntrypoint.count)
          on (nd.documentId === ds.documentId))

      from(main)(row => select(row.key, row.measures))
    }

    /** @return (nodeId, tagId, count) tuples. */
    def allSearchResultCountsByNodeId: FinderResult[(Long, Long, Long)] = {
      val main: Query[GroupWithMeasures[Product2[Long, Long], Long]] = join(query, Schema.documentSearchResults)((nd, ds) =>
        groupBy(nd.nodeId, ds.searchResultId)
          compute (org.overviewproject.postgres.SquerylEntrypoint.count)
          on (nd.documentId === ds.documentId))

      from(main)(row => select(row.key._1, row.key._2, row.measures))
    }

    def untaggedDocumentCountsByNodeId: FinderResult[(Long, Long)] = {
      val main: Query[GroupWithMeasures[Long, Long]] = join(query, Schema.documentTags.leftOuter)((nd, dt) =>
        where(dt.map(_.tagId).getOrElse(-1l) isNull) // Not clear if get would be safe here
          groupBy (nd.nodeId)
          compute (org.overviewproject.postgres.SquerylEntrypoint.count)
          on (nd.documentId === dt.map(_.documentId)))

      from(main)(row => select(row.key, row.measures))
    }
  }
  implicit private def queryToNodeDocumentFinderResult(query: Query[NodeDocument]): NodeDocumentFinderResult = new NodeDocumentFinderResult(query)

  def byDocumentSet(documentSet: Long): NodeDocumentFinderResult = byDocumentSetQuery(documentSet)
  
  /** @return All NodeDocuments with the given Node IDs */
  def byNodeIds(nodeIds: Traversable[Long]): NodeDocumentFinderResult = {
    Schema.nodeDocuments.where(_.nodeId in nodeIds)
  }

  /** @ return All NodeDocuments with the given Node IDs in the given Tree. */
  def byNodeIdsInTree(nodeIds: Traversable[Long], treeId: Long): NodeDocumentFinderResult = {
    from(Schema.nodeDocuments, Schema.nodes, Schema.trees)((nd, n, t) =>
      where((nd.nodeId in nodeIds) and (nd.nodeId === n.id) and (n.rootId === t.rootNodeId) and (t.id === treeId))
      select(nd)
    )
  }

}
