package controllers.backend

import scala.concurrent.Future
import scala.slick.lifted.{Column,Query,RunnableCompiled}

import models.pagination.{Page,PageInfo,PageRequest}
import org.overviewproject.database.{SlickClient,SlickSessionProvider}

trait DbBackend extends SlickClient with SlickSessionProvider {
  def list[T](q: Query[_, T, Seq]): Future[Seq[T]] = db { session =>
    import org.overviewproject.database.Slick.simple._
    q.list(session)
  }

  def list[T](q: RunnableCompiled[_ <: Query[_, _, Seq], Seq[T]]): Future[Seq[T]] = db { session =>
    import org.overviewproject.database.Slick.simple._
    q.list(session)
  }

  def firstOption[T](q: Query[_, T, Seq]): Future[Option[T]] = db { session =>
    import org.overviewproject.database.Slick.simple._
    q.firstOption(session)
  }

  def firstOption[T](q: RunnableCompiled[_ <: Query[_, _, Seq], Seq[T]]): Future[Option[T]] = db { session =>
    import org.overviewproject.database.Slick.simple._
    q.firstOption(session)
  }

  /** Returns a Page[T] based on an item query, uncompiled.
    *
    * This is the only way to handle `WHERE ... IN (...)` queries. It takes
    * more CPU than `RunnableCompiled` queries.
    */
  def page[T](itemsQ: Query[_, T, Seq], countQ: Column[Int], pageRequest: PageRequest): Future[Page[T]] = db { implicit session =>
    import org.overviewproject.database.Slick.simple._

    // Sequential, so Postgres can benefit from a hot cache on the second query
    val items: Seq[T] = itemsQ.list(session)
    val count: Int = countQ.run
    Page(items, PageInfo(pageRequest, count))
  }

  /** Returns a Page[T] based on item and count queries.
    *
    * The itemsQ is compiled, so it must already include
    * `.drop(offset).take(limit)`. That makes `pageRequest` seems a redundant,
    * but let's not lose sleep over it.
    */
  def page[T](itemsQ: RunnableCompiled[_ <: Query[_, _, Seq], Seq[T]], countQ: RunnableCompiled[_, Int], pageRequest: PageRequest): Future[Page[T]] = db { implicit session =>
    import org.overviewproject.database.Slick.simple._
    // Sequential, so Postgres can benefit from a hot cache on the second query
    val items: Seq[T] = itemsQ.list
    val count: Int = countQ.run
    Page(items, PageInfo(pageRequest, count))
  }

  def emptyPage[T](pageRequest: PageRequest) = Future.successful(Page(Seq[T](), PageInfo(pageRequest, 0)))
}
