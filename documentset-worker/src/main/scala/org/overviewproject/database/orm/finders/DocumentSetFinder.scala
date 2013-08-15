package org.overviewproject.database.orm.finders


import org.overviewproject.database.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.DocumentSet
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }

object DocumentSetFinder extends Finder {

  type DocumentSetResult = FinderResult[DocumentSet]
  
  def byDocumentSet(documentSet: Long): DocumentSetResult = 
    Schema.documentSets.where(_.id === documentSet)
  
}