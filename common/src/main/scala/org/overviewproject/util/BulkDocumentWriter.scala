package org.overviewproject.util

import scala.collection.mutable.Buffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import org.overviewproject.database.SlickSessionProvider
import org.overviewproject.models.Document
import org.overviewproject.models.tables.Documents
import org.overviewproject.searchindex.TransportIndexClient

/** Writes documents to the database and/or search index in bulk.
  *
  * FIXME replace this with an Actor system.
  *
  * Usage:
  *
  * <pre>
  *   val bulkWriter = BulkDocumentWriter.forDatabaseAndSearchIndex
  *   val seqViewOfFutures = seqViewOfDocuments.map(bulkWriter.addAndFlushIfNeeded)
  *   val allDone = Future.sequence(seqViewOfFutures).andThen(bulkWriter.flush)
  * </pre>
  *
  * Be sure to call flush() when you're done: otherwise some documents won't
  * be written.
  *
  * <strong>Warning</strong>: the whole point of this buffer is to avoid memory
  * overflows. Do not add a document until after the previous add is finished.
  *
  * @param maxNDocuments: flushes after adding this number of documents (or before)
  * @param maxNBytes: flushes after adding this number of bytes of documents (or before)
  */
trait BulkDocumentWriter {
  val maxNDocuments: Int = 1000
  val maxNBytes: Int = 5 * 1024 * 1024

  private var currentBuffer: Buffer[Document] = Buffer()
  private var currentNBytes: Int = 0

  /** Actual flush operation. */
  protected def flushImpl(documents: Iterable[Document]): Future[Unit]

  private def needsFlush: Boolean = {
    currentBuffer.length >= maxNDocuments || currentNBytes >= maxNBytes
  }

  /** Adds a document, and potentially flushes everything. */
  def addAndFlushIfNeeded(document: Document): Future[Unit] = synchronized {
    currentBuffer.append(document)
    currentNBytes += document.title.length + document.suppliedId.length + document.text.length + document.url.getOrElse("").length
    if (needsFlush) {
      flush
    } else {
      Future.successful(())
    }
  }

  /** Flushes everything.
    *
    * If there is nothing to flush, this is a no-op.
    */
  def flush: Future[Unit] = synchronized {
    if (currentBuffer.isEmpty) {
      Future.successful(())
    } else {
      val documents = currentBuffer
      currentBuffer = Buffer()
      currentNBytes = 0
      flushImpl(documents)
    }
  }
}

object BulkDocumentWriter extends SlickSessionProvider {
  def forDatabaseAndSearchIndex: BulkDocumentWriter = new BulkDocumentWriter {
    override def flushImpl(documents: Iterable[Document]) = {
      val dbFuture = flushDocumentsToDatabase(documents)
      val siFuture = flushDocumentsToSearchIndex(documents)

      for {
        _ <- dbFuture
        _ <- siFuture
      } yield ()
    }
  }

  def forSearchIndex: BulkDocumentWriter = new BulkDocumentWriter {
    override def flushImpl(documents: Iterable[Document]) = flushDocumentsToSearchIndex(documents)
  }

  private lazy val insertInvoker = {
    import org.overviewproject.database.Slick.simple._
    Documents.insertInvoker
  }
  private def flushDocumentsToDatabase(documents: Iterable[Document]): Future[Unit] = db { session =>
    insertInvoker.++=(documents)(session)
  }

  private lazy val indexClient = TransportIndexClient.singleton
  private def flushDocumentsToSearchIndex(documents: Iterable[Document]): Future[Unit] = {
    indexClient.addDocuments(documents)
  }
}
