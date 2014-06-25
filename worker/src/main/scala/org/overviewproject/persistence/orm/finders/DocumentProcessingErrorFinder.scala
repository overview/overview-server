package org.overviewproject.persistence.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.persistence.orm.Schema.documentProcessingErrors
import org.overviewproject.tree.orm.finders.Finder
import org.overviewproject.tree.orm.finders.FinderResult
import org.squeryl.dsl.Measures

object DocumentProcessingErrorFinder extends Finder {

  def countByDocumentSet(documentSetId: Long): Long =
    from(documentProcessingErrors)(dpe =>
      where (dpe.documentSetId ===  documentSetId)
      compute(count)
    )
}