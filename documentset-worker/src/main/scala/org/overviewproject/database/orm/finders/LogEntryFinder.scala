package org.overviewproject.database.orm.finders

import org.overviewproject.database.orm.Schema
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }
import org.overviewproject.tree.orm.LogEntry

object LogEntryFinder extends Finder {
  type LogEntryFinderResult = FinderResult[LogEntry]

  def byDocumentSet(documentSetId: Long): LogEntryFinderResult = {
    from(Schema.logEntries)(le =>
      where(le.documentSetId === documentSetId)
        select (le))
  }
}