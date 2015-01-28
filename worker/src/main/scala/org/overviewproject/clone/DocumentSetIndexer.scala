package org.overviewproject.clone

import scala.concurrent.Future

import org.overviewproject.persistence.orm.finders.DocumentFinder
import org.overviewproject.searchindex.TransportIndexClient
import org.overviewproject.util.BulkDocumentWriter

object DocumentSetIndexer {
  private def await[A](f: Future[A]): A = {
    scala.concurrent.Await.result(f, scala.concurrent.duration.Duration.Inf)
  }

  def indexDocuments(documentSetId: Long): Future[Unit] = {
    val bulkWriter = BulkDocumentWriter.forSearchIndex

    // XXX We use await() so we can stick with the same DB cursor
    await(TransportIndexClient.singleton.addDocumentSet(documentSetId))
    DocumentFinder
      .byDocumentSet(documentSetId)
      .foreach { document => await(bulkWriter.addAndFlushIfNeeded(document.toDocument)) }

    bulkWriter.flush
  }
}
