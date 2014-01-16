package org.overviewproject.database.orm.finders

import scala.language.implicitConversions
import org.overviewproject.database.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.Document
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }
import org.squeryl.Query

object DocumentFinder extends Finder {

  class DocumentFinderResult(query: Query[Document]) extends FinderResult(query) {

    def toFileIds: FinderResult[Option[Long]] =
      from(query)(d => select(d.fileId))
  }

  implicit private def queryToDocumentFinderResult(query: Query[Document]): DocumentFinderResult = new DocumentFinderResult(query)

  def byDocumentSetAndDocumentCloudIds(documentSet: Long, documentCloudIds: Iterable[String]): DocumentFinderResult =
    Schema.documents.where(d => d.documentSetId === documentSet and
      (d.documentcloudId in (documentCloudIds)))

  def byDocumentSet(documentSetId: Long): DocumentFinderResult =
    Schema.documents.where(d => d.documentSetId === documentSetId)

}