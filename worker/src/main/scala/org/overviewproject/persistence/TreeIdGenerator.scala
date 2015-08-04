package com.overviewdocs.persistence

import com.overviewdocs.postgres.SquerylEntrypoint._
import com.overviewdocs.database.DeprecatedDatabase
import com.overviewdocs.persistence.orm.Schema.trees

object TreeIdGenerator {
  def next(documentSetId: Long): Long = DeprecatedDatabase.inTransaction {
    val maxId: Option[Long] = from(trees)(t =>
      where (t.documentSetId === documentSetId)
      compute(max(t.id))
      )
      
    val baseId: Long = maxId.getOrElse(documentSetId << 32)
    baseId + 1
  }
}
