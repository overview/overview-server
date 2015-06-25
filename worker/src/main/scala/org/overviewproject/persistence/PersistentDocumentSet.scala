package org.overviewproject.persistence

import org.overviewproject.database.HasBlockingDatabase
import org.overviewproject.models.tables.DocumentSets
import org.overviewproject.util.SortedDocumentIdsRefresher

trait PersistentDocumentSet extends HasBlockingDatabase {
  import database.api._

  private lazy val countsByIdCompiled = Compiled { (documentSetId: Rep[Long]) =>
    DocumentSets
      .filter(_.id === documentSetId)
      .map(ds => (ds.documentCount, ds.importOverflowCount))
  }

  def updateDocumentSetCounts(documentSetId: Long, documentCount: Int, overflowCount: Int): Unit = {
    blockingDatabase.runUnit(countsByIdCompiled(documentSetId).update((documentCount, overflowCount)))
  }

  def refreshSortedDocumentIds(documentSetId: Long): Unit = {
    scala.concurrent.Await.result(
      SortedDocumentIdsRefresher.refreshDocumentSet(documentSetId),
      scala.concurrent.duration.Duration.Inf
    )
  }
}
