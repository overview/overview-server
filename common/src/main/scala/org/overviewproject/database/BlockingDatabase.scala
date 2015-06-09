package org.overviewproject.database

import scala.concurrent.Future
import scala.language.higherKinds
import slick.dbio.DBIO
import slick.lifted.RunnableCompiled

import org.overviewproject.database.Slick.api._

/** Like a Database, but adds an await() to each method.
  *
  * Do not use this class! This method is evil. Await is evil. It's only to
  * promote deprecated code to slightly-less-ugly deprecated code.
  */
class BlockingDatabase(val database: Database) {
  private def await[T](f: Future[T]): T = {
    scala.concurrent.Await.result(f, scala.concurrent.duration.Duration.Inf)
  }

  val largeObjectManager = database.largeObjectManager

  def run[T](action: DBIO[T]) = await(database.run(action))
  def runUnit[T](action: DBIO[T]) = await(database.runUnit(action))
  def seq[T](query: Rep[Seq[T]]) = await(database.seq(query))
  def seq[T](query: RunnableCompiled[_, Seq[T]]) = await(database.seq(query))
  def option[T](action: DBIO[Seq[T]]) = await(database.option(action))
  def option[T](query: Query[_, T, Seq]) = await(database.option(query))
  def option[T](query: RunnableCompiled[_, Seq[T]]) = await(database.option(query))
  def length(query: Query[_, _, Seq]) = await(database.length(query))
  def delete(query: Query[_ <: Table[_], _, Seq]) = await(database.delete(query))
  def delete[RU, C[_]](query: RunnableCompiled[_ <: Query[_, _, C], C[RU]]) = await(database.delete(query))
}
