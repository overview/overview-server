package org.overviewproject.persistence

import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.database.Database
import org.overviewproject.persistence.orm.Schema.trees

object TreeIdGenerator {
  def next(documentSetId: Long): Long = Database.inTransaction {
    val maxId: Option[Long] = from(trees)(t =>
      where (t.documentSetId === documentSetId)
      compute(max(t.id))
      )
      
    val baseId: Long = maxId.getOrElse(documentSetId << 32)
    baseId + 1
  }
}