package models.orm.finders

import org.squeryl.Query
import org.squeryl.dsl.GroupWithMeasures
import scala.language.implicitConversions
import scala.language.postfixOps
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.Document
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }

import models.orm.Schema
import models.Selection

object DocumentFinder extends Finder {
  class DocumentFinderResult(query: Query[Document]) extends FinderResult(query) {
    def withTagsAsStrings: FinderResult[(Document, Option[String])] = {
      val tagStringsByDocumentId = join(Schema.documentTags, Schema.tags.leftOuter)((dt, t) =>
        groupBy(dt.documentId)
          compute (string_agg(t.map(_.name), ","))
          on (dt.tagId === t.map(_.id)))

      join(query, tagStringsByDocumentId.leftOuter)((d, ts) =>
        select(d, ts.flatMap(_.measures))
          on (d.id === ts.map(_.key)))
    }

    def withTagsAsLongStrings: FinderResult[(Document, Option[String])] = {
      val tagLongStringsByDocumentId = from(Schema.documentTags)(dt =>
        groupBy(dt.documentId)
          compute (string_agg(format("%s", dt.tagId), ",")))

      join(query, tagLongStringsByDocumentId.leftOuter)((d, ts) =>
        select(d, ts.flatMap(_.measures))
          on (d.id === ts.map(_.key)))
    }

    /** Returns just the IDs. */
    def toIds: FinderResult[Long] = {
      from(query)(d => select(d.id))
    }

    /**
     * Returns just the IDs.
     *
     * The IDs will be for documents ordered by title, description and ID.
     */
    def toIdsOrdered: FinderResult[Long] = {
      from(query)(d =>
        select(d.id)
          orderBy (d.title, d.description, d.id))
    }

    /**
     * Returns (Document,nodeIdsString,tagIdsString) tuples.
     *
     * Squeryl does not work with Option[Array] at this time, so we use
     * string_agg instead of array_agg.
     */
    def withNodeIdsAndTagIdsAsLongStrings: FinderResult[(Document, Option[String], Option[String])] = {
      val nodeIdStrings: Query[GroupWithMeasures[Long, Option[String]]] = join(query, Schema.nodeDocuments)((q, nd) =>
        groupBy(nd.documentId)
          compute (string_agg(cast(nd.nodeId, "varchar"), ","))
          on (nd.documentId === q.id))

      val tagIdStrings: Query[GroupWithMeasures[Long, Option[String]]] = join(query, Schema.documentTags)((q, dt) =>
        groupBy(dt.documentId)
          compute (string_agg(cast(dt.tagId, "varchar"), ","))
          on (dt.documentId === q.id))

      join(query, nodeIdStrings.leftOuter, tagIdStrings.leftOuter)((d, n, t) =>
        select(d, n.flatMap(_.measures), t.flatMap(_.measures))
          orderBy (d.title, d.description, d.id)
          on (d.id === n.map(_.key), d.id === t.map(_.key)))
    }
  }
  implicit private def queryToDocumentFinderResult(query: Query[Document]): DocumentFinderResult = new DocumentFinderResult(query)

  /**
   * @return All `Document`s with the given ID.
   *
   * This can have 0 or 1 row.
   */
  def byId(id: Long): DocumentFinderResult = {
    Schema.documents.where(_.id === id)
  }

  /** @return All `Document`s with any of the given IDs. */
  def byIds(ids: Traversable[Long]): DocumentFinderResult = {
    Schema.documents.where(_.id in ids)
  }

  /** @return All `Document`s with the given DocumentSet. */
  def byDocumentSet(documentSet: Long): DocumentFinderResult = {
    Schema.documents.where(_.documentSetId === documentSet)
  }

  /** @return All `Document`s in the given Selection. */
  def bySelection(selection: Selection): DocumentFinderResult = {
    var query = Schema.documents.where(_.documentSetId === selection.documentSetId)

    if (selection.nodeIds.nonEmpty) {
      val idsFromNodes = from(Schema.nodeDocuments)(nd =>
        where(nd.nodeId in selection.nodeIds)
          select (nd.documentId))
      query = query.where(_.id in idsFromNodes)
    }

    if (selection.tagIds.nonEmpty) {
      val idsFromTags = from(Schema.documentTags)(dt =>
        where(dt.tagId in selection.tagIds)
          select (dt.documentId))
      query = query.where(_.id in idsFromTags)
    }

    if (selection.searchResultIds.nonEmpty) {
      val idsFromSearchResults = from(Schema.documentSearchResults)(dsr =>
        where(dsr.searchResultId in selection.searchResultIds)
          select (dsr.documentId))
      query = query.where(_.id in idsFromSearchResults)
    }

    if (selection.documentIds.nonEmpty) {
      query = query.where(_.id in selection.documentIds)
    }

    if (selection.untagged) {
      val treesInDocumentSet = from(Schema.trees)(t =>
        where(t.documentSetId === selection.documentSetId)
        select(t.id))
        
      val parentNode = from(Schema.nodes)(n =>
        where((n.treeId in treesInDocumentSet) and (n.parentId isNull))
          select (n.id))
      val documentsInNodes = from(Schema.nodeDocuments)(nd =>
        where(nd.nodeId in parentNode)
          select (nd.documentId))

      val tags = from(Schema.tags)(t =>
        where(t.documentSetId === selection.documentSetId)
          select (t.id))
          
      val taggedDocuments = from(Schema.documentTags)(dt =>
        where (dt.tagId in tags)
        select (dt.documentId))
        
      query = query.where(d => (d.id notIn taggedDocuments) and (d.id in documentsInNodes))
    }

    query
  }
}
