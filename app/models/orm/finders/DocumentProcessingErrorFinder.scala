package models.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.DocumentProcessingError
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }

import models.orm.Schema

object DocumentProcessingErrorFinder extends Finder {
  def byDocumentSet(documentSet: Long) : FinderResult[DocumentProcessingError] = {
    from(Schema.documentProcessingErrors)(dpe =>
      where(dpe.documentSetId === documentSet)
      select(dpe)
    )
  }
}
