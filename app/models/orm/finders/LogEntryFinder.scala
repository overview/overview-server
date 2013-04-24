package models.orm.finders

import org.squeryl.Query
import scala.language.implicitConversions

import org.overviewproject.postgres.SquerylEntrypoint._
import models.orm.{ LogEntry, Schema, User }

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
