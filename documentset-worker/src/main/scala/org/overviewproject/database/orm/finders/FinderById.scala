package org.overviewproject.database.orm.finders

import org.overviewproject.tree.orm.finders.Finder
import org.overviewproject.postgres.SquerylEntrypoint._
import org.squeryl.Table
import org.overviewproject.tree.orm.finders.FinderResult
import org.squeryl.KeyedEntity

class FinderById[T <: KeyedEntity[Long]](table: Table[T]) extends Finder {
  
  def byId(id: Long): FinderResult[T] = table.where(_.id === id)
}