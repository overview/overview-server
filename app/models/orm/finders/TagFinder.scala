package models.orm.finders

import org.squeryl.dsl.GroupWithMeasures
import scala.language.implicitConversions
import scala.language.postfixOps

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.Tag
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }

import org.squeryl.Query

import models.orm.Schema

object TagFinder extends Finder {
  class TagResult(query: Query[Tag]) extends FinderResult(query) {
    def withCounts : FinderResult[(Tag,Long)] = {
      join(toQuery, tagCounts.toQuery.leftOuter)((tags, counts) =>
        select((tags, &(nvl(counts.map(_.measures), 0L))))
        on(tags.id === counts.map(_.key))
      )
    }

    def withCountsForDocumentSetAndTree(tree: Long) : FinderResult[(Tag,Long,Long)] = {
      join(toQuery, tagCounts.toQuery.leftOuter, tagCountsInTree(tree).toQuery.leftOuter)((t, dsc, tc) =>
        select((t, &(nvl(dsc.map(_.measures), 0L)), &(nvl(tc.map(_.measures), 0L))))
        on(t.id === dsc.map(_.key), t.id === tc.map(_.key))
      )
    }
  }
  object TagResult {
    implicit def fromQuery(query: Query[Tag]) = new TagResult(query)
  }

  /** @return A mapping from Tag ID to document count */
  def tagCounts : FinderResult[GroupWithMeasures[Long,Long]] = {
    from(Schema.documentTags)(dt =>
      groupBy(dt.tagId)
      compute(count)
    )
  }

  /** @return A mapping from Tag ID to count for the given Tree.  */
  def tagCountsInTree(tree: Long) : FinderResult[GroupWithMeasures[Long,Long]] = {
    /*
     * node (1) -> node_document (many) -> document_tag (many).
     *
     * This may be slow.
     */
    join(Schema.documentTags, Schema.nodeDocuments, Schema.nodes, Schema.trees)((dt, nd, n, t) =>
      where(t.id === tree)
      groupBy(dt.tagId)
      compute(count(dt.documentId))
      on(
        dt.documentId === nd.documentId,
        nd.nodeId === n.id,
        n.id === t.rootNodeId // we only count for the root node
      )
    )
  }

  /** @return All `Tag`s with the given DocumentSet. */
  def byDocumentSet(documentSet: Long) : TagResult = {
    from(Schema.tags)(t =>
      where(t.documentSetId === documentSet)
      select(t)
      orderBy(t.name)
    )
  }

  /** @return All `Tag`s with the given DocumentSet and ID. */
  def byDocumentSetAndId(documentSet: Long, id: Long) : TagResult = {
    Schema.tags.where((t) => t.documentSetId === documentSet and t.id === id)
  }
}
