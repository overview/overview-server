package models.orm.finders

import scala.language.implicitConversions

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.DocumentTag
import org.overviewproject.tree.orm.finders.{ BaseDocumentTagFinder, FinderResult }

import org.squeryl.Query

import models.SelectionRequest
import models.orm.Schema

object DocumentTagFinder extends BaseDocumentTagFinder(Schema.documentTags, Schema.tags) {
  class DocumentTagFinderResult(query: Query[DocumentTag]) extends FinderResult[DocumentTag](query) {
    def toDocumentIds: Query[Long] = {
      from(query)(q => select(q.documentId))
    }

    def toTagIds: Query[Long] = {
      from(query)(q => select(q.tagId))
    }
  }

  implicit def queryToDocumentTagFinderResult(query: Query[DocumentTag]) : DocumentTagFinderResult = new DocumentTagFinderResult(query)

  def byDocumentSet(documentSet: Long) : DocumentTagFinderResult = byDocumentSetQuery(documentSet)
  
  def byTag(tag: Long) : DocumentTagFinderResult = {
    Schema.documentTags.where(_.tagId === tag)
  }

  def byTagAndSelection(tag: Long, selection: SelectionRequest) : DocumentTagFinderResult = {
    val documentIds = DocumentFinder.bySelectionRequest(selection).toIds
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
