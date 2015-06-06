package org.overviewproject.persistence

import org.overviewproject.database.DeprecatedDatabase
import org.overviewproject.postgres.SquerylEntrypoint._
import org.overviewproject.persistence.orm.Schema.documentSets
import org.overviewproject.util.SortedDocumentIdsRefresher

trait PersistentDocumentSet {
  def updateDocumentSetCounts(documentSetId: Long, documentCount: Int, overflowCount: Int): Unit = {

    DeprecatedDatabase.inTransaction {
      update(documentSets)(ds =>
        where(ds.id === documentSetId)
          set (ds.importOverflowCount := overflowCount,
               ds.documentCount := documentCount))
    }
  }

  def refreshSortedDocumentIds(documentSetId: Long): Unit = {
    scala.concurrent.Await.result(
      SortedDocumentIdsRefresher.refreshDocumentSet(documentSetId),
      scala.concurrent.duration.Duration.Inf
    )
  }
}
