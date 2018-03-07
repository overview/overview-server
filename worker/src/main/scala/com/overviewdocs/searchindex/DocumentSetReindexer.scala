package com.overviewdocs.searchindex

import scala.concurrent.Future

import com.overviewdocs.database.HasDatabase
import com.overviewdocs.models.Document
import com.overviewdocs.models.tables.Documents
import com.overviewdocs.util.Logger

trait DocumentSetReindexer {
  protected val indexClient: IndexClient
  def reindexDocumentSet(documentSetId: Long): Future[Unit]
}

object DocumentSetReindexer extends DocumentSetReindexer with HasDatabase {
  private val logger = Logger.forClass(getClass)

  import database.api._
  import database.executionContext

  override protected val indexClient = LuceneIndexClient.onDiskSingleton // TODO use actor

  private val NDocumentsPerBatch = 30 // ~1MB/document max

  def reindexDocumentSet(documentSetId: Long): Future[Unit] = {
    logger.info("Reindexing DocumentSet {}", documentSetId)
    for {
      _ <- indexClient.removeDocumentSet(documentSetId)
      _ <- indexClient.addDocumentSet(documentSetId)
      _ <- indexEachDocument(documentSetId)
    } yield {
      logger.info("Finished reindexing DocumentSet {}", documentSetId)
    }
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
      Future.unit
    }
  }

  private def indexEachDocument(documentSetId: Long): Future[Unit] = {
    database.seq(Documents.filter(_.documentSetId === documentSetId).map(_.id)).flatMap { allIds =>
      val idsIt = allIds.grouped(NDocumentsPerBatch)
      indexRemainingBatches(documentSetId, idsIt)
    }
  }
}
