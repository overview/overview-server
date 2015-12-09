package com.overviewdocs.clone

import play.api.libs.iteratee.{Enumerator,Iteratee}
import play.api.libs.streams.Streams
import scala.concurrent.Future

import com.overviewdocs.database.HasDatabase
import com.overviewdocs.searchindex.ElasticSearchIndexClient
import com.overviewdocs.util.BulkDocumentWriter
import com.overviewdocs.models.Document
import com.overviewdocs.models.tables.Documents

trait Indexer {
  def indexDocuments(documentSetId: Long): Future[Unit]
}

object Indexer extends Indexer with HasDatabase {
  import database.api._
  import database.executionContext

  def indexDocuments(documentSetId: Long): Future[Unit] = {
    val bulkWriter = BulkDocumentWriter.forSearchIndex

    for {
      _ <- ElasticSearchIndexClient.singleton.addDocumentSet(documentSetId)
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
