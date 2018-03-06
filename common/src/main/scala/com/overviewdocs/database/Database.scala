package com.overviewdocs.database

import java.sql.SQLException
import javax.sql.DataSource
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import scala.language.higherKinds
import slick.dbio.DBIO
import slick.jdbc.JdbcBackend.{Database=>DatabaseFactory}
import slick.lifted.RunnableCompiled
import slick.util.AsyncExecutor
import com.typesafe.config.ConfigFactory

import com.overviewdocs.database.Slick.api._

/** Runs queries on a Postgres database.
  *
  * These methods act upon Slick DBIOActions. All are asynchronous: they run in
  * the Slick executor.
  *
  * Use it like this:
  *
  * ```
  * val db = new Database(dataSource)
  * import db.api._
  * db.seq(Documents.filter(_.documentSetId === documentSetId))
  * ```
  *
  * Most of the time, you'll want to extend the HasDatabase trait instead of
  * invoking this class directly.
  * The constructor is private to force use of the singleton object returned by
  * `Database()`.
  */
class Database(val slickDatabase: slick.jdbc.JdbcBackend.Database) {
  /** Exposes the Slick Database API.
    *
    * This saves typing over "com.overviewdocs.database.Slick.api".
    */
  val api = com.overviewdocs.database.Slick.api

  /** Exposes a "standard" execution context for flatMap operations.
    *
    * Again, this is to save typing. You'll need an execution context when
    * running code like this:
    *
    * ```
    * import database.executionContext
    * val x = database.run(for {
    *   foo &lt;- dbio1
    *   bar &lt;- dbio2
    * } yield bar)
    * ```
    *
    * The database operations occur in Slick's execution context. The flatMap
    * logic occurs in this one.
    */
  implicit val executionContext = scala.concurrent.ExecutionContext.Implicits.global

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
  def runUnit[T](action: DBIO[T]): Future[Unit] = run(action).map(_ => ())

  /** Returns all the results from a query as a Seq.
    *
    * Usage:
    *
    * ```
    * import com.overviewdocs.database.Slick.api._
    * database.seq(Documents.filter(_.documentSetId === documentSetId))
    * ```
    */
  def seq[E, U, C[_]](query: Query[E, U, C]): Future[Vector[U]] = {
    run(query.to[Vector].result)
  }

  /** Return all the results from a compiled query as a Seq.
    *
    * Usage:
    *
    * ```
    * import com.overviewdocs.database.Slick.api._
    * lazy val compiledQuery = Compiled { (documentSetId: Rep[Long]) =&gt;
    *   Documents.filter(_.documentSetId === documentSetId)
    * }
    * database.seq(compiledQuery(documentSetId))
    * ```
    */
  def seq[T](query: RunnableCompiled[_, Seq[T]]): Future[Vector[T]] = run(query.result).map(_.toVector)

  /** Returns an Option with the first row from the query, if there is one.
    *
    * Usage:
    *
    * ```
    * database.option(sql"SELECT id FROM document".as[Long])
    */
  def option[T](action: DBIO[Seq[T]]): Future[Option[T]] = {
    run(action).map(_.headOption)
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
    * database.option(compiledQuery(documentId))
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
      case "42704" => new exceptions.UndefinedObject(e)
      case _ => e
    }
    case _ => t
  }

  /** Re-casts Future failures to Conflict or ParentMissing when appropriate.
    */
  def wrapExceptions[T](future: Future[T]): Future[T] = {
    future.transform(identity, wrapException)
  }
}

object Database {
  private lazy val database: Database = connectToGlobalDatabaseAndWaitUntilSuccess

  private def connectToGlobalDatabaseAndWaitUntilSuccess: Database = {
    val path = "db.default"
    val config = ConfigFactory.load.getConfig(path)
      .withFallback(ConfigFactory.parseString(s"""
        poolName: "$path",
        numThreads: 5,
        registerMbeans: false,
        queueSize: 1000
      """))

    while (true) {
      val jdbcDataSource = slick.jdbc.hikaricp.HikariCPJdbcDataSource.forConfig(config, null, path, slick.util.ClassLoaderUtil.defaultClassLoader)

      try {
        jdbcDataSource.ds.getConnection.close // will throw if cannot connect

        // If we're here, then connecting works! Now to return a Database.
				// Copied from Slick's internals
				val poolName = config.getString("poolName")
				val numThreads = config.getInt("numThreads")
				val maxConnections = jdbcDataSource.maxConnections.fold(numThreads*5)(identity)
				val registerMbeans = config.getBoolean("registerMbeans")
				val executor = AsyncExecutor(poolName, numThreads, numThreads, config.getInt("queueSize"),
					maxConnections, registerMbeans = registerMbeans)
				val slickDatabase = DatabaseFactory.forSource(jdbcDataSource, executor)
				return new Database(slickDatabase)
      } catch {
        case e: java.sql.SQLTransientConnectionException => {
          System.err.println("Failed to connect to database. Will retry in 1s...")
          Thread.sleep(1000)
        }
      }
    }
    ???
  }
  
  def apply(): Database = database
}
