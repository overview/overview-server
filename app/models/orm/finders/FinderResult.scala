package models.orm.finders

import org.squeryl.Query
import org.overviewproject.postgres.SquerylEntrypoint
import scala.language.implicitConversions

import org.overviewproject.postgres.SquerylEntrypoint

/** A query with useful conversions.
  *
  * A FinderResult is a query that has not yet been run. It may be created
  * outside of a database transaction; however, one must open a database
  * connection to iterate over it or count it. (That's when the query will
  * be executed.)
  *
  * A FinderResult can be cast to an Iterable. The same rules apply to the
  * Iterable. It can be clearer, in some APIs, to require a FinderResult
  * instead of an Iterable, even if they would otherwise serve the same
  * purpose. That signals to the caller that the callee will be fetching from
  * a database.
  */
class FinderResult[A](val query: Query[A]) {
  def toQuery : Query[A] = query
  def toIterable : Iterable[A] = {
    SquerylEntrypoint.queryToIterable(query)
  }
  def headOption : Option[A] = query.headOption // avoid double implicit conversion
  def count : Long = {
    import org.overviewproject.postgres.SquerylEntrypoint._
    val countQuery = from(query)(_ => compute(SquerylEntrypoint.count))
    countQuery.headOption.getOrElse(throw new AssertionError("Count did not return anything")).measures
  }
}
object FinderResult {
  implicit def finderResultToQuery[A](r: FinderResult[A]) = r.toQuery
  implicit def finderResultToIterable[A](r: FinderResult[A]) = r.toIterable
}
