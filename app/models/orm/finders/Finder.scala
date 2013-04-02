package models.orm.finders

import org.squeryl.Query
import scala.language.implicitConversions

import org.overviewproject.postgres.SquerylEntrypoint

trait Finder {
  class FinderResult[A](val query: Query[A]) {
    def toQuery : Query[A] = query
    def toIterable : Iterable[A] = {
      SquerylEntrypoint.queryToIterable(query)
    }
    def headOption : Option[A] = query.headOption // avoid double implicit conversion
  }
  object FinderResult {
    implicit def finderResultToQuery[A](r: FinderResult[A]) = r.toQuery
    implicit def finderResultToIterable[A](r: FinderResult[A]) = r.toIterable
  }

  implicit protected def queryToFinderResult[A](query: Query[A]) = {
    new FinderResult(query)
  }
}
