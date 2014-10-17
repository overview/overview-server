package models.orm.finders

import org.squeryl.Query
import org.squeryl.dsl.GroupWithMeasures
import scala.language.implicitConversions
import scala.language.postfixOps
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.Document
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }

import models.orm.Schema
import models.SelectionRequest

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
          orderBy (d.title, d.pageNumber, d.description, d.id)
          on (d.id === n.map(_.key), d.id === t.map(_.key)))
    }
    
    /** 
     *  Returns file related info: (title, fileId, pageId, pageNumber)
     *  All fields are optional.
     */
    def toFileInfo: FinderResult[(Option[String], Option[Long], Option[Long], Option[Int] )] = 
      from(query)(d =>
        select (d.title, d.fileId, d.pageId, d.pageNumber))
        
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
}
