package com.overviewdocs.searchindex

import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.query.Query
import com.overviewdocs.models.Document

/** Index client that writes directly to Lucene.
  *
  * Internally, this client uses a MruLuceneIndexCache to keep recently-opened
  * files open.
  */
trait LuceneIndexClient extends IndexClient {
  /** Thread pool for blocking I/O.
    *
    * Lucene does synchronous blocking I/O, but a Scala caller wouldn't expect
    * that. So you need to build this trait with an ExecutionContext where all
    * Lucene operations will take place.
    */
  protected implicit val ec: ExecutionContext

  /** Open or create an index for a documentSetId we have not seen recently.
    *
    * A LuceneIndexClient will not call this method twice concurrently for the
    * same DocumentSet, nor will it call `.close()` on a DocumentSetLuceneIndex
    * while opening one with the same ID.
    *
    * We assume openIndex() will block. This method will be invoked in a
    * `Future()`.
    */
  protected def openIndex(documentSetId: Long): DocumentSetLuceneIndex

  protected lazy val cache: MruLuceneIndexCache = {
    new MruLuceneIndexCache(
      loader=documentSetId => openIndex(documentSetId),
      executionContext=ec
    )
  }

  protected def getIndex(documentSetId: Long): Future[DocumentSetLuceneIndex] = cache.get(documentSetId)

  /** Deletes all indices -- BE CAREFUL!
    *
    * Useful in unit tests.
    */
  override def deleteAllIndices: Future[Unit] = ???

  override def addDocumentSet(id: Long): Future[Unit] = {
    getIndex(id).map(_ => ())
  }

  override def removeDocumentSet(id: Long): Future[Unit] = {
    getIndex(id).map(_.delete)
  }

  override def addDocuments(id: Long, documents: Iterable[Document]): Future[Unit] = {
    getIndex(id).map(_.addDocuments(documents))
  }

  override def searchForIds(id: Long, q: Query): Future[Seq[Long]] = {
    getIndex(id).map(_.searchForIds(q))
  }

  override def highlight(documentSetId: Long, documentId: Long, q: Query) = {
    getIndex(documentSetId).map(_.highlight(documentId, q))
  }

  override def highlights(documentSetId: Long, documentIds: Seq[Long], q: Query) = {
    getIndex(documentSetId).map(_.highlights(documentIds, q))
  }

  override def refresh(documentSetId: Long): Future[Unit] = {
    getIndex(documentSetId).map(_.refresh)
  }
}

object LuceneIndexClient {
  def createInMemoryLuceneIndexClient(implicit executionContext: ExecutionContext): LuceneIndexClient = {
    new LuceneIndexClient {
      override protected implicit val ec: ExecutionContext = executionContext
      override protected def openIndex(documentSetId: Long) = {
        new DocumentSetLuceneIndex(new org.apache.lucene.store.RAMDirectory)
      }
    }
  }

  lazy val onDiskSingleton: LuceneIndexClient = new LuceneIndexClient {
    import com.typesafe.config.ConfigFactory
    import java.nio.file.Paths

    private val baseDirectory = ConfigFactory.load().getString("search.baseDirectory")
    private val threadPool = java.util.concurrent.Executors.newFixedThreadPool(4) // Hard-coded!
    override protected implicit val ec: ExecutionContext = ExecutionContext.fromExecutorService(threadPool)

    override protected def openIndex(documentSetId: Long) = {
      val rootPath = Paths.get(baseDirectory, documentSetId.toString)
      val lockFactory = new org.apache.lucene.store.SingleInstanceLockFactory
      val directory = org.apache.lucene.store.FSDirectory.open(rootPath, lockFactory)
      val ret = new DocumentSetLuceneIndex(directory)
      ret.refresh // if it's a new index, write it to disk so we can read from it
      ret
    }
  }
}
