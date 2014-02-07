package org.overviewproject.persistence.orm.finders

import scala.language.{ implicitConversions, postfixOps }
import org.overviewproject.persistence.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.Document
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }
import org.squeryl.Query

object DocumentFinder extends Finder {

  class DocumentFinderResult(query: Query[Document]) extends FinderResult(query) {
    def orderedById: DocumentFinderResult = 
      from(query)(d => 
        select (d)
        orderBy(d.id asc))
  }
  implicit private def queryToDocumentFinderResult(query: Query[Document]): DocumentFinderResult = new DocumentFinderResult(query)
  
  def byDocumentSet(documentSet: Long): DocumentFinderResult = {
    Schema.documents.where(d => d.documentSetId === documentSet)
  }

}