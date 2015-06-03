package controllers.backend

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future
import slick.lifted.RunnableCompiled

import models.pagination.{Page,PageInfo,PageRequest}
import org.overviewproject.database.Slick.api._
import org.overviewproject.database.{SlickClient,SlickSessionProvider}

trait DbBackend extends SlickClient with SlickSessionProvider {
  /** Returns a Seq with all the results from the given query.
    */
  def list[T](q: Rep[Seq[T]]): Future[Seq[T]] = run(q.result)

  /** Returns a Seq with all the results from the given query.
    */
  def list[T](q: RunnableCompiled[_, Seq[T]]): Future[Seq[T]] = run(q.result)

  /** Returns a Seq with the first result from the given query.
    *
    * The query will have LIMIT 1 appended, to save bandwidth in case of
    * programmer error.
    */
  def firstOption[T](q: Query[_, T, Seq]): Future[Option[T]] = run(q.take(1).result.headOption)

  /** Returns a Seq with all the results from the given query.
    *
    * Ensure the query will not return lots of results: even though we are
    * only querying for one, all rows will be transferred over the wire.
    */
  def firstOption[T](q: RunnableCompiled[_, Seq[T]]): Future[Option[T]] = run(q.result).map(_.headOption)

  /** Executes the given Slick Action.
    *
    * SQL exceptions will be typed as in controllers.backend.exceptions.
    */
  def run[T](action: DBIO[T]): Future[T] = exceptions.wrap(slickDb.run(action))

  /** Executes the given Slick Action and returns nothing.
    *
    * SQL exceptions will be typed as in controllers.backend.exceptions.
    */
  def runUnit(action: DBIO[_]): Future[Unit] = run(action).map(_ => ())

  /** Returns a Page[T] based on an item query, uncompiled.
    *
    * This is the only way to handle `WHERE ... IN (...)` queries. It takes
    * more CPU than `RunnableCompiled` queries.
    */
  def page[T](itemsQ: Rep[Seq[T]], countQ: Rep[Int], pageRequest: PageRequest): Future[Page[T]] = {
    // Sequential, so Postgres can benefit from a hot cache on the second query
    val action = for {
      items <- itemsQ.result
      count <- countQ.result
    } yield Page(items, PageInfo(pageRequest, count))

    exceptions.wrap(slickDb.run(action))
  }

  /** Returns a Page[T] based on item and count queries.
    *
    * The itemsQ is compiled, so it must already include
    * `.drop(offset).take(limit)`. That makes `pageRequest` seems a redundant,
    * but let's not lose sleep over it.
    */
  def page[T](itemsQ: RunnableCompiled[_, Seq[T]], countQ: RunnableCompiled[_, Int], pageRequest: PageRequest): Future[Page[T]] = {
    // Sequential, so Postgres can benefit from a hot cache on the second query
    val action = for {
      items <- itemsQ.result
      count <- countQ.result
    } yield Page(items, PageInfo(pageRequest, count))

    exceptions.wrap(slickDb.run(action))
  }

  def emptyPage[T](pageRequest: PageRequest) = Future.successful(Page(Seq[T](), PageInfo(pageRequest, 0)))
}
