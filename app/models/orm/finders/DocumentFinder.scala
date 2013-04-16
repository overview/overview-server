package models.orm.finders

import org.squeryl.Query
import scala.language.implicitConversions
import scala.language.postfixOps

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.Document
import models.orm.Schema

object DocumentFinder extends Finder {
  class DocumentFinderResult(query: Query[Document]) extends FinderResult(query) {
    def withTagsAsStrings : FinderResult[(Document,Option[String])] = {
      val tagStringsByDocumentId = join(Schema.documentTags, Schema.tags.leftOuter)((dt, t) =>
        groupBy(dt.documentId)
        compute(string_agg(t.map(_.name), ","))
        on(dt.tagId === t.map(_.id))
      )

      join(query, tagStringsByDocumentId.leftOuter)((d, ts) =>
        select(d, ts.flatMap(_.measures))
        on(d.id === ts.map(_.key))
      )
    }

    def withTagsAsLongStrings : FinderResult[(Document,Option[String])] = {
      val tagLongStringsByDocumentId = from(Schema.documentTags)(dt =>
        groupBy(dt.documentId)
        compute(string_agg(format("%s", dt.tagId), ","))
      )

      join(query, tagLongStringsByDocumentId.leftOuter)((d, ts) =>
        select(d, ts.flatMap(_.measures))
        on(d.id === ts.map(_.key))
      )
    }
  }
  implicit private def queryToDocumentFinderResult(query: Query[Document]) = new DocumentFinderResult(query)

  /** @return All `Document`s with the given ID.
    *
    * This can have 0 or 1 row.
    */
  def byId(id: Long) : DocumentFinderResult = {
    Schema.documents.where(_.id === id)
  }

  /** @return All `Document`s with the given DocumentSet. */
  def byDocumentSet(documentSet: Long) : DocumentFinderResult = {
    Schema.documents.where(_.documentSetId === documentSet)
  }
}
