package com.overviewdocs.csv

import java.nio.{ByteBuffer,CharBuffer}
import java.nio.charset.{CharsetDecoder,CoderResult,CodingErrorAction}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

import com.overviewdocs.database.{HasDatabase,LargeObject,TreeIdGenerator}
import com.overviewdocs.models.{CsvImport,DocumentProcessingError,Tree}
import com.overviewdocs.models.tables.{CsvImports,Documents,DocumentProcessingErrors,DocumentSets,Tags,Trees}
import com.overviewdocs.searchindex.{IndexClient,ElasticSearchIndexClient}
import com.overviewdocs.util.RecalculateDocumentSetCaches

/** Processes a CSV Import. */
class CsvImporter(
  csvImport: CsvImport,

  /** Search index client.
    *
    * We call addDocumentSet() on this when done.
    */
  indexClient: IndexClient = ElasticSearchIndexClient.singleton,

  /** Number of bytes to process at a time.
    *
    * A larger buffer makes processing faster (up to a point); a smaller buffer
    * makes Overview update progress and check for cancellation more frequently.
    * Aim for a progress report every 1s at the longest.
    */
  bufferSize: Int = 5 * 1024 * 1024
) extends HasDatabase {
  private var nBytesProcessed: Long = 0
  private var nDocumentsWritten: Int = 0
  private var unparsedBytes: Array[Byte] = Array[Byte]()

  private val decoder: CharsetDecoder = csvImport.charset.newDecoder
    .onMalformedInput(CodingErrorAction.REPLACE)
    .onUnmappableCharacter(CodingErrorAction.REPLACE)
  private val csvParser = new CsvParser
  private val csvDocumentProducer = new CsvDocumentProducer

  private lazy val csvDocumentWriterFuture: Future[CsvDocumentWriter] = {
    import database.api._

    for {
      maxDocumentId <- database.run(Documents.filter(_.documentSetId === csvImport.documentSetId).map(_.id).max.result)
      existingTags <- database.seq(Tags.filter(_.documentSetId === csvImport.documentSetId))
    } yield {
      new CsvDocumentWriter(csvImport.documentSetId, maxDocumentId, existingTags)
    }
  }

  private[csv] sealed trait NextStep
  private[csv] object NextStep {
    /** Run another step(). */
    case object Continue extends NextStep

    /** Delete the CsvImport, and optionally create a DocumentProcessingError.
      */
    case class Finish(error: Option[String]) extends NextStep
  }

  /** All logic.
    *
    * At the start, the `csv_import` in the database may be anything. At the end
    * of processing, we guarantee:
    *
    * * The large object will be deleted.
    * * `n_bytes_processed` will equal `n_bytes`
    * * We will add documents to the database.
    * * We may add a document processing error to the database.
    */
  def run: Future[Unit] = {
    step(bufferSize).flatMap(_ match {
      case NextStep.Continue => run
      case NextStep.Finish(error) => finish(error)
    })
  }

  /** Update document set counts and ensure it's searchable. */
  private def finish(error: Option[String]): Future[Unit] = {
    for {
      _ <- indexClient.addDocumentSet(csvImport.documentSetId)
      _ <- RecalculateDocumentSetCaches.run(csvImport.documentSetId)
      _ <- deleteCsvImport(error)
      _ <- createTree
    } yield ()
  }

  /** Mark stuff in the database so we never resume this import. */
  private def cancel: Future[Unit] = {
    Future.successful(())
  }

  /** Process some bytes from the blob, write some documents to the database,
    * report process to the database, and check for cancellation.
    *
    * @param bufferSize Number of bytes to read.
    * @return What to do next.
    */
  private[csv] def step(bufferSize: Int): Future[NextStep] = {
    for {
      bytes <- readBytes(bufferSize)
      _ <- processBytes(bytes)
      result <- reportProgressAndDecideWhatsNext
    } yield result
  }

  /** Writes all the documents (and Tags and DocumentTags) we've read so far
    * to the database; update nBytesProcessed and nDocumentsWritten.
    */
  private def processBytes(bytes: Array[Byte]): Future[Unit] = {
    val allBytes = unparsedBytes ++ bytes
    val byteBuffer = ByteBuffer.wrap(allBytes)

    // We know the number of chars is <= the number of bytes
    val chars = new Array[Char](byteBuffer.limit)
    val charBuffer = CharBuffer.wrap(chars)

    var isLastBatch: Boolean = nBytesProcessed + bytes.length >= csvImport.nBytes

    val coderResult1 = decoder.decode(byteBuffer, charBuffer, isLastBatch)
    assert(coderResult1 == CoderResult.UNDERFLOW)

    if (isLastBatch) {
      val coderResult2 = decoder.flush(charBuffer)
      assert(coderResult2 == CoderResult.UNDERFLOW)
    }

    val nUnparsedBytes = byteBuffer.remaining
    unparsedBytes = new Array[Byte](nUnparsedBytes)
    byteBuffer.get(unparsedBytes)

    nBytesProcessed += bytes.length

    csvParser.write(chars, 0, charBuffer.position)
    if (isLastBatch) csvParser.end

    csvParser.getParsedRows.foreach(csvDocumentProducer.addCsvRow)
    csvParser.clearParsedRows

    val newCsvDocuments = csvDocumentProducer.getProducedDocuments
      .drop(Math.max(0, csvImport.nDocuments - nDocumentsWritten))
    nDocumentsWritten += csvDocumentProducer.getProducedDocuments.length
    csvDocumentProducer.clearProducedDocuments

    for {
      csvDocumentWriter <- csvDocumentWriterFuture
      _ <- { newCsvDocuments.foreach(csvDocumentWriter.add); csvDocumentWriter.flush }
    } yield ()
  }

  /** Returns the next buffer full of bytes from the database.
    */
  private def readBytes(bufferSize: Int): Future[Array[Byte]] = {
    import database.api._

    database.run((for {
      lo <- database.largeObjectManager.open(csvImport.loid, LargeObject.Mode.Read)
      _ <- lo.seek(nBytesProcessed)
      bytes <- lo.read(bufferSize)
    } yield bytes).transactionally)
  }

  /** Writes current progress to the database; returns what we should do next.
    */
  private def reportProgressAndDecideWhatsNext: Future[NextStep] = {
    import database.api._

    val q = sql"""
      UPDATE csv_import
      SET n_bytes_processed = $nBytesProcessed, n_documents = $nDocumentsWritten
      WHERE id = ${csvImport.id}
      RETURNING cancelled
    """.as[Boolean]

    database.option(q).map(_ match {
      case Some(false) if csvImport.nBytes > nBytesProcessed => NextStep.Continue
      case Some(true) => NextStep.Finish(Some("Overview stopped adding documents because you cancelled processing this CSV"))
      case _ if csvParser.isFullyParsed => NextStep.Finish(None)
      case _ => NextStep.Finish(Some("Overview stopped adding documents because this is not a valid CSV"))
    })
  }

  private def updateDocumentSetCount: Future[Unit] = {
    import database.api._
    database.runUnit(sqlu"""
      UPDATE document_set
      SET document_count = (SELECT COUNT(*) FROM document WHERE document_set_id = document_set.id)
      WHERE id = ${csvImport.documentSetId}
    """)
  }

  private lazy val byId = {
    import database.api._
    Compiled { csvImportId: Rep[Long] =>
      CsvImports.filter(_.id === csvImportId)
    }
  }

  private def maybeCreateDocumentProcessingError(maybeError: Option[String]) = {
    import database.api._
    DocumentProcessingErrors.map(_.createAttributes) ++= maybeError.toSeq.map { error =>
      DocumentProcessingError.CreateAttributes(
        csvImport.documentSetId,
        csvImport.filename,
        error,
        None,
        None
      )
    }
  }

  private def deleteCsvImport(error: Option[String]): Future[Unit] = {
    import database.api._
    database.runUnit((for {
      _ <- maybeCreateDocumentProcessingError(error)
      _ <- database.largeObjectManager.unlink(csvImport.loid)
      _ <- byId(csvImport.id).delete
    } yield ()).transactionally)
  }

  private def createTree: Future[Unit] = {
    import database.api._

    for {
      treeId <- TreeIdGenerator.next(csvImport.documentSetId)
      _ <- database.runUnit(Trees.+=(Tree.CreateAttributes(
        documentSetId=csvImport.documentSetId,
        lang=csvImport.lang
      ).toTreeWithId(treeId)))
    } yield ()
  }
}
