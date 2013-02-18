package org.overviewproject.persistence

import org.overviewproject.database.Database
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.persistence.orm.Schema.documentSets

trait PersistentDocumentSet {
  def updateOverflowCount(documentSetId: Long, overflowCount: Int): Unit = {

    Database.inTransaction {
      update(documentSets)(ds =>
        where(ds.id === documentSetId)
          set (ds.importOverflowCount := overflowCount))
    }
  }
}