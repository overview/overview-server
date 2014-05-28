package org.overviewproject.persistence.orm.finders

import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }
import org.overviewproject.tree.orm.DocumentSet
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.persistence.orm.Schema.documentSets

object DocumentSetFinder extends Finder {
  type DocumentSetFinderResult  = FinderResult[DocumentSet]
  
  def byId(documentSetId: Long): DocumentSetFinderResult = 
    from(documentSets)(ds =>
      where (ds.id === documentSetId)
      select(ds)
    )
}