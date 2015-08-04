package com.overviewdocs.tree.orm.finders

import com.overviewdocs.postgres.SquerylEntrypoint._
import org.squeryl.Table
import org.squeryl.KeyedEntity

class FinderById[T <: KeyedEntity[Long]](table: Table[T]) extends Finder {
  
  def byId(id: Long): FinderResult[T] = table.where(_.id === id)
}

object FinderById {
  def apply[T <: KeyedEntity[Long]](table: Table[T]): FinderById[T] = new FinderById(table)
}