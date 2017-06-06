package com.overviewdocs.searchindex

import java.time.Instant
import scala.collection.mutable
import scala.concurrent.{ExecutionContext,Future}

class MruLuceneIndexCache(
  val loader: Long => DocumentSetLuceneIndex,
  val nConcurrentDocumentSets: Int = 20,
  val executionContext: ExecutionContext
) {
  private implicit val ec: ExecutionContext = executionContext

  private case class Entry(var lastAccess: Instant, val index: DocumentSetLuceneIndex)

  private val active = mutable.Map.empty[Long,Entry]

  private def closeWorstIndexSync: Unit = {
    // This is O(1) because nConcurrentDocumentSets is constant (and small)
    var (worstId, worstEntry) = active.minBy({ case (key, entry) => entry.lastAccess })
    worstEntry.index.close
    active -= worstId
  }

  private def getSync(documentSetId: Long): DocumentSetLuceneIndex = synchronized {
    active.get(documentSetId) match {
      case Some(entry) => {
        entry.lastAccess = Instant.now()
        entry.index
      }
      case None => {
        while (active.size >= nConcurrentDocumentSets) {
          closeWorstIndexSync
        }
        val index = loader(documentSetId)
        val entry = Entry(Instant.now, index)
        active += documentSetId -> entry
        index
      }
    }
  }

  def get(documentSetId: Long): Future[DocumentSetLuceneIndex] = Future(getSync(documentSetId))

  /** Removes all reference to an index.
    *
    * Call this after calling index.delete.
    */
  def remove(documentSetId: Long): Unit = synchronized {
    active.-=(documentSetId)
  }
}
