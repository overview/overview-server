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
  implicit class DocumentFinderResult(query: Query[Document]) extends FinderResult(query) {
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
  }

  /** @return All `Document`s with the given DocumentSet. */
  def byDocumentSet(documentSet: Long): DocumentFinderResult = {
    Schema.documents.where(_.documentSetId === documentSet)
  }
}
