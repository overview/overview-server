package models.orm.finders

import scala.language.implicitConversions

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.DocumentTag
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }

import org.squeryl.Query

import models.Selection
import models.orm.Schema

object DocumentTagFinder extends Finder {
  class DocumentTagFinderResult(query: Query[DocumentTag]) extends FinderResult[DocumentTag](query) {
    def toDocumentIds: Query[Long] = {
      from(query)(q => select(q.documentId))
    }

    def toTagIds: Query[Long] = {
      from(query)(q => select(q.tagId))
    }
  }

  implicit def queryToDocumentTagFinderResult(query: Query[DocumentTag]) : DocumentTagFinderResult = new DocumentTagFinderResult(query)

  def byDocumentSet(documentSet: Long) : DocumentTagFinderResult = {
    // Join through tags should be faster: there are usually fewer tags than documents
    // Select as WHERE with a subquery, to circumvent Squeryl delete() missing the join
    val tagIds = from(Schema.tags)(t =>
      where(t.documentSetId === documentSet)
      select(t.id)
    )

    Schema.documentTags.where(_.tagId in tagIds)
  }

  def byTag(tag: Long) : DocumentTagFinderResult = {
    Schema.documentTags.where(_.tagId === tag)
  }

  def byTagAndSelection(tag: Long, selection: Selection) : DocumentTagFinderResult = {
    val documentIds = DocumentFinder.bySelection(selection).toIds
    /*
    This breaks Squeryl's Schema.delete(Query[A]).
    Schema.documentTags
      .where(_.tagId === tag)
      .where(_.documentId in documents)
    ...from() doesn't.
    */
    from(Schema.documentTags)(dt =>
      where(dt.tagId === tag and (dt.documentId in documentIds))
      select(dt)
    )
  }
}
