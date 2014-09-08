package org.overviewproject.tree.orm.finders

import org.overviewproject.postgres.SquerylEntrypoint._
import org.squeryl.Table
import org.squeryl.KeyedEntity

class FinderById[T <: KeyedEntity[Long]](table: Table[T]) extends Finder {
  
  def byId(id: Long): FinderResult[T] = table.where(_.id === id)
}