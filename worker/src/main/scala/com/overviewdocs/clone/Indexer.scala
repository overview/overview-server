package com.overviewdocs.clone

import scala.concurrent.Future

import com.overviewdocs.database.HasDatabase
import com.overviewdocs.searchindex.{LuceneIndexClient,IndexClient}
import com.overviewdocs.models.Document
import com.overviewdocs.models.tables.Documents

trait Indexer {
  protected val indexClient: IndexClient
  def indexDocuments(documentSetId: Long): Future[Unit]
}

object Indexer extends Indexer with HasDatabase {
  import database.api._
  import database.executionContext

  override protected val indexClient = LuceneIndexClient.onDiskSingleton // TODO use actor

  private val NDocumentsPerBatch = 30 // ~1MB/document max

  def indexDocuments(documentSetId: Long): Future[Unit] = {
    for {
      _ <- indexClient.addDocumentSet(documentSetId)
      _ <- indexEachDocument(documentSetId)
    } yield ()
  }

  private def indexRemainingBatches(documentSetId: Long, idsIt: Iterator[Seq[Long]]): Future[Unit] = {
    if (idsIt.hasNext) {
      val ids: Seq[Long] = idsIt.next
      val step: Future[Unit] = for {
        documents <- database.seq(Documents.filter(_.id inSet ids))
        _ <- indexClient.addDocuments(documentSetId, documents)
      }  yield ()
      step.flatMap(_ => indexRemainingBatches(documentSetId, idsIt))
    } else {
      Future.successful(())
    }
  }

  private def indexEachDocument(documentSetId: Long): Future[Unit] = {
    database.seq(Documents.filter(_.documentSetId === documentSetId).map(_.id)).flatMap { allIds =>
      val idsIt = allIds.grouped(NDocumentsPerBatch)
      indexRemainingBatches(documentSetId, idsIt)
    }
  }
}
