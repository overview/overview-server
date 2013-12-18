package models.orm.finders

import scala.language.implicitConversions
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.tree.orm.finders.{ Finder, FinderResult }
import org.squeryl.Query
import models.orm.{ Schema, User }
import org.overviewproject.tree.orm.LogEntry

object LogEntryFinder extends Finder {
  class LogEntryResult(query: Query[LogEntry]) extends FinderResult(query) {
    def withUsers : FinderResult[(LogEntry,User)] = {
      join(toQuery, Schema.users)((le, u) =>
        select(le, u)
        orderBy(le.date.desc)
        on(le.userId === u.id)
      )
    }
  }
  object LogEntryResult {
    implicit def fromQuery(query: Query[LogEntry]) = new LogEntryResult(query)
  }

  /** @return All `LogEntry`s for a DocumentSet. */
  def byDocumentSet(documentSet: Long) : LogEntryResult = {
    from(Schema.logEntries)(le =>
      where(le.documentSetId === documentSet)
      select(le)
      orderBy(le.date.desc)
    )
  }
}
