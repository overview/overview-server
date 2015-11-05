package com.overviewdocs.persistence

import com.overviewdocs.database.HasBlockingDatabase
import com.overviewdocs.models.tables.Trees

object TreeIdGenerator extends HasBlockingDatabase {
  import database.api._

  lazy val query = Compiled { documentSetId: Rep[Long] =>
    Trees
      .filter(_.documentSetId === documentSetId)
      .map(_.id)
      .max
  }

  def next(documentSetId: Long): Long = {
    blockingDatabase.run(query(documentSetId).result).getOrElse(documentSetId << 32) + 1
  }
}
