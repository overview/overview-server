package com.overviewdocs.util

import java.io.{ByteArrayInputStream,ByteArrayOutputStream,DataOutputStream}
import java.nio.charset.Charset
import org.postgresql.PGConnection
import scala.collection.mutable.Buffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.overviewdocs.database.{Database,HasDatabase}
import com.overviewdocs.models.Document
import com.overviewdocs.models.tables.Documents
import com.overviewdocs.searchindex.ElasticSearchIndexClient

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

  private val Millennium = 946684800000L // 2000-01-01 since the epoch, in ms

  /** Actual flush operation. */
  protected def flushImpl(documents: Iterable[Document]): Future[Unit]

  def needsFlush: Boolean = {
    currentBuffer.length >= maxNDocuments || currentNBytes >= maxNBytes
  }

  /** Adds a document; never flushes.
    *
    * The caller should call needsFlush after and flush if necessary.
    */
  def add(document: Document): Unit = synchronized {
    currentBuffer.append(document)
    currentNBytes += document.title.length + document.suppliedId.length + document.text.length + document.url.map(_.length).getOrElse(0)
  }

  /** Adds a document, and potentially flushes everything. */
  def addAndFlushIfNeeded(document: Document): Future[Unit] = synchronized {
    add(document)
    flushIfNeeded
  }

  private def flushIfNeeded: Future[Unit] = {
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

  protected def flushDocumentsToDatabase(database: Database, documents: Iterable[Document]): Future[Unit] = {
    import com.overviewdocs.database.Slick.api._
    import slick.dbio.SynchronousDatabaseAction
    import slick.jdbc.JdbcBackend
    import slick.util.DumpInfo

    // This method is in the trait (not companion object) because we test it.
    val out = new ByteArrayOutputStream
    val dataOut = new DataOutputStream(out)
    val charset = Charset.forName("utf-8")

    // Binary COPY format: http://www.postgresql.org/docs/9.4/static/sql-copy.html
    // Header: "PGCOPY\n\0xff\r\n\0"
    dataOut.writeBytes("PGCOPY\n")
    dataOut.writeByte(0xff)
    dataOut.writeBytes("\r\n")
    dataOut.writeByte(0)

    // Flags
    dataOut.writeInt(0)

    // Header extension area length
    dataOut.writeInt(0)

    def writeBoolean(b: Boolean) = { dataOut.writeInt(1); dataOut.writeByte(if (b) 1 else 0) }
    def writeInt(i: Int) = { dataOut.writeInt(4); dataOut.writeInt(i) }
    def writeIntOption(i: Option[Int]) = i match {
      case Some(j) => writeInt(j)
      case None => dataOut.writeInt(-1)
    }
    def writeLong(i: Long) = { dataOut.writeInt(8); dataOut.writeLong(i) }
    def writeLongOption(i: Option[Long]) = i match {
      case Some(j) => writeLong(j)
      case None => dataOut.writeInt(-1)
    }
    def writeString(s: String) = {
      val b = s.getBytes(charset)
      dataOut.writeInt(b.length)
      dataOut.write(b)
    }
    def writeStringOption(s: Option[String]) = s match {
      case Some(t) => writeString(t)
      case None => dataOut.writeInt(-1)
    }
    def writeTimestamp(d: java.util.Date) = writeLong((d.getTime() - Millennium) * 1000L)
    
    // Tuples
    // Tracks models/tables/Documents.scala and models/Document.scala
    documents.foreach { document =>
      dataOut.writeShort(15) // Number of fields

      writeLong(document.id)
      writeLong(document.documentSetId)
      writeStringOption(document.url)
      writeString(document.suppliedId)
      writeString(document.title)
      writeIntOption(document.pageNumber)
      writeString(document.keywords.mkString(" "))
      writeTimestamp(document.createdAt)
      writeLongOption(document.fileId)
      writeLongOption(document.pageId)
      writeString(document.metadataJson.toString)
      writeString(document.text)
      writeString(document.displayMethod.toString)
      writeBoolean(document.isFromOcr)
      writeStringOption(document.thumbnailLocation)
    }

    // File trailer
    dataOut.writeShort(-1)

    dataOut.flush()
    val bytes = out.toByteArray
    val bytesAsInputStream = new ByteArrayInputStream(bytes)

    database.run(new SynchronousDatabaseAction[Unit,NoStream,JdbcBackend,Effect.Write] {
      override def getDumpInfo = DumpInfo("COPY document", s"${bytes.length} bytes")
      override def run(context: JdbcBackend#Context): Unit = {
        val pgConnection: PGConnection = context.connection.unwrap(classOf[PGConnection])
        val copyManager = pgConnection.getCopyAPI

        copyManager.copyIn("""
          COPY document (
            id,
            document_set_id,
            url,
            supplied_id,
            title,
            page_number,
            description,
            created_at,
            file_id,
            page_id,
            metadata_json_text,
            text,
            display_method,
            is_from_ocr,
            thumbnail_location
          )
          FROM STDIN
          BINARY
        """, bytesAsInputStream)
      }
    })
  }
}

object BulkDocumentWriter extends HasDatabase {
  import database.api._

  def forDatabaseAndSearchIndex: BulkDocumentWriter = new BulkDocumentWriter {
    override def flushImpl(documents: Iterable[Document]) = {
      val dbFuture = flushDocumentsToDatabase(database, documents)
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

  def forDatabase: BulkDocumentWriter = new BulkDocumentWriter {
    override def flushImpl(documents: Iterable[Document]) = flushDocumentsToDatabase(database, documents)
  }

  private lazy val indexClient = ElasticSearchIndexClient.singleton

  private def flushDocumentsToSearchIndex(documents: Iterable[Document]): Future[Unit] = {
    val byDocumentSetId: Map[Long, Iterable[Document]] = documents.groupBy(_.documentSetId)
    // Run all the commits at once. Probably not ideal.
    val futures: Iterable[Future[Unit]] = byDocumentSetId.map { case (dsId, docs) =>
      indexClient.addDocuments(dsId, docs)
    }
    Future.sequence(futures).map(_ => ())
  }
}
