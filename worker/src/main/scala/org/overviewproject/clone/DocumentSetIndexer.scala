package org.overviewproject.clone

import play.api.libs.iteratee.{Enumerator,Iteratee}
import play.api.libs.streams.Streams
import scala.concurrent.Future

import org.overviewproject.database.HasDatabase
import org.overviewproject.searchindex.TransportIndexClient
import org.overviewproject.util.BulkDocumentWriter
import org.overviewproject.models.Document
import org.overviewproject.models.tables.Documents

object DocumentSetIndexer extends HasDatabase {
  import database.api._
  import database.executionContext

  def indexDocuments(documentSetId: Long): Future[Unit] = {
    val bulkWriter = BulkDocumentWriter.forSearchIndex

    for {
      _ <- TransportIndexClient.singleton.addDocumentSet(documentSetId)
      _ <- indexEachDocument(documentSetId, bulkWriter)
      _ <- bulkWriter.flush
    } yield ()
  }

  private def indexEachDocument(documentSetId: Long, bulkWriter: BulkDocumentWriter): Future[Unit] = {
    val publisher = database.slickDatabase.stream(Documents.filter(_.documentSetId === documentSetId).result)
    val enumerator = Streams.publisherToEnumerator(publisher)
    val iteratee = Iteratee.foldM(()) { (s: Unit, document: Document) => bulkWriter.addAndFlushIfNeeded(document) }
    enumerator.run(iteratee)
  }
}
