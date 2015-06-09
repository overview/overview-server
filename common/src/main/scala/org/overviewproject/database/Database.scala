package org.overviewproject.database

import java.sql.SQLException
import javax.sql.{DataSource=>JDataSource}
import scala.concurrent.Future
import scala.language.higherKinds
import slick.dbio.DBIO
import slick.jdbc.JdbcBackend.{Database=>DatabaseFactory}
import slick.lifted.RunnableCompiled

import org.overviewproject.database.Slick.api._

/** Runs queries on a Postgres database.
  *
  * These methods act upon Slick DBIOActions. All are asynchronous: they run in
  * the Slick executor.
  *
  * Use it like this:
  *
  * ```
  * import org.overviewproject.database.Slick.api._
  * val db = new Database(dataSource)
  * db.seq(Documents.filter(_.documentSetId === documentSetId))
  * ```
  *
  * Most of the time, you'll want to extend the HasDatabase trait instead of
  * invoking this class directly.
  */
class Database(val dataSource: JDataSource) {
  /** Exposes the Slick Database. */
  val slickDatabase = DatabaseFactory.forDataSource(dataSource)
  val executionContext = scala.concurrent.ExecutionContext.Implicits.global

  /** Provides access to large objects.
    *
    * @see LargeObjectManager
    */
  val largeObjectManager: LargeObjectManager = new LargeObjectManager(this)

  /** Runs a DBIO (Action) and returns the result.
    *
    * This is the lowest-level method in the class.
    */
  def run[T](action: DBIO[T]): Future[T] = wrapExceptions(slickDatabase.run(action))

  /** Like `run()`, but ignores the return value. */
  def runUnit[T](action: DBIO[T]): Future[Unit] = run(action).map(_ => ())(slickDatabase.ioExecutionContext)

  /** Returns all the results from a query as a Seq.
    *
    * Usage:
    *
    * ```
    * import org.overviewproject.database.Slick.api._
    * database.seq(Documents.filter(_.documentSetId === documentSetId))
    * ```
    */
  def seq[T](query: Rep[Seq[T]]): Future[Seq[T]] = run(query.result)

  /** Return all the results from a compiled query as a Seq.
    *
    * Usage:
    *
    * ```
    * import org.overviewproject.database.Slick.api._
    * lazy val compiledQuery = Compiled { (documentSetId: Rep[Long]) =&gt;
    *   Documents.filter(_.documentSetId === documentSetId)
    * }
    * database.seq(compiledQuery(documentSetId))
    * ```
    */
  def seq[T](query: RunnableCompiled[_, Seq[T]]): Future[Seq[T]] = run(query.result)

  /** Returns an Option with the first row from the query, if there is one.
    *
    * Usage:
    *
    * ```
    * database.option(sqlu"SELECT id FROM document".as[Long])
    */
  def option[T](action: DBIO[Seq[T]]): Future[Option[T]] = {
    run(action).map(_.headOption)(slickDatabase.ioExecutionContext)
  }

  /** Returns an Option with the first row from the query, if there is one.
    *
    * Usage:
    *
    * ```
    * database.option(Documents.filter(_.id === id))
    * ```
    */
  def option[T](query: Query[_, T, Seq]): Future[Option[T]] = run(query.result.headOption)

  /** Returns an Option with the first row from the query, if there is one.
    *
    * Usage:
    *
    * ```
    * lazy val compiledQuery = Compiled { (id: Rep[Long]) =&gt;
    *   Documents.filter(_.id === id)
    * }
    * database.seq(compiledQuery(documentId))
    * ```
    */
  def option[T](query: RunnableCompiled[_, Seq[T]]): Future[Option[T]] = option(query.result)

  /** Returns the result of a COUNT(*) query.
    *
    * Usage:
    *
    * ```
    * database.length(Documents.filter(_.documentSetId === documentSetId))
    * ```
    */
  def length(query: Query[_, _, Seq]): Future[Int] = run(query.length.result)

  /** Destroys all rows the query would return.
    *
    * Usage:
    *
    * ```
    * database.delete(Documents.filter(_.documentSetId === documentSetId))
    * ```
    *
    * There is no return value, because `Future[Unit]` is most often the result
    * you want to return. If you want to check how many rows were deleted, drop
    * the layer of abstraction:
    *
    * ```
    * database.run(Documents.filter(_.documentSetId === documentSetId).delete)
    * ```
    */
  def delete(query: Query[_ <: Table[_], _, Seq]): Future[Unit] = runUnit(query.delete)

  /** Destroys all rows the query would return.
    *
    * Usage:
    *
    * ```
    * lazy val documentsByDocumentSetId = Compiled { (documentSetId: Rep[Long]) =gt;
    *   Documents.filter(_.documentSetId === documentSetId)
    * }
    * database.delete(documentsByDocumentSetId(documentSetId))
    * ```
    *
    * There is no return value, because `Future[Unit]` is most often the result
    * you want to return. If you want to check how many rows were deleted, drop
    * the layer of abstraction:
    *
    * ```
    * database.run(Documents.filter(_.documentSetId === documentSetId).delete)
    * ```
    */
  def delete[RU, C[_]](query: RunnableCompiled[_ <: Query[_, _, C], C[RU]]): Future[Unit] = runUnit(query.delete)

  def wrapException(t: Throwable): Throwable = t match {
    case e: SQLException => e.getSQLState() match {
      case "23505" => new exceptions.Conflict(e)
      case "23503" => new exceptions.ParentMissing(e)
      case _ => e
    }
    case _ => t
  }

  /** Re-casts Future failures to Conflict or ParentMissing when appropriate.
    */
  def wrapExceptions[T](future: Future[T]): Future[T] = {
    future.transform(identity, wrapException)(slickDatabase.ioExecutionContext)
  }
}
