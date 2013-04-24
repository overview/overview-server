package models.orm.stores

import org.squeryl.{ KeyedEntity, KeyedEntityDef, Query, Table }

import org.overviewproject.postgres.SquerylEntrypoint._

class BaseStore[A](protected val table: Table[A]) {
  implicit protected val ked: KeyedEntityDef[A,_] = table.ked.getOrElse(throw new AssertionError("Need KeyedEntityDef"))

  def insertOrUpdate(a: A): A = {
    table.insertOrUpdate(a)
  }

  def insertBatch(as: Iterable[A]) : Unit = {
    table.insert(as)
  }

  def delete(query: Query[A]): Unit = {
    table.delete(query)
  }

  def delete[K](k: K)(implicit ked: KeyedEntityDef[A,K]) : Unit = {
    table.delete(k)
  }
}
