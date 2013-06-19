package org.overviewproject.tree.orm.stores

import org.squeryl.{ KeyedEntity, KeyedEntityDef, Query, Table }
import org.squeryl.dsl.QueryDsl

import org.overviewproject.postgres.SquerylEntrypoint._

class BaseStore[A](protected val table: Table[A]) {
  implicit protected val ked: KeyedEntityDef[A,_] = table.ked.getOrElse(throw new AssertionError("Need KeyedEntityDef"))

  def insertOrUpdate(a: A): A = {
    table.insertOrUpdate(a)
  }

  def insertBatch(as: Iterable[A]) : Unit = {
    table.insert(as)
  }

  /** FIXME this is not type-safe. Be sure T relates to A properly. */
  def insertSelect[T](q: Query[T]) : Int = {
    table.insertSelect(q)
  }

  def delete(query: Query[A]): Int = {
    table.delete(query)
  }

  def delete[K](k: K)(implicit ked: KeyedEntityDef[A,K]) : Unit = {
    table.delete(k)
  }
}
