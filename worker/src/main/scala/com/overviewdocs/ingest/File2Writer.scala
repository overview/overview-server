package com.overviewdocs.ingest

import akka.util.ByteString
import akka.stream.{IOResult,Materializer}
import akka.stream.scaladsl.{FileIO,Keep,Sink,Source}
import com.typesafe.config.ConfigFactory
import java.time.Instant
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest
import play.api.libs.json.JsObject
import scala.collection.immutable
import scala.concurrent.{ExecutionContext,Future}

import com.overviewdocs.blobstorage.{BlobBucketId,BlobStorage}
import com.overviewdocs.database.Database
import com.overviewdocs.models.{BlobStorageRef,File2}
import com.overviewdocs.models.tables.File2s
import com.overviewdocs.ingest.models.{BlobStorageRefWithSha1,CreatedFile2,WrittenFile2,ProcessedFile2,IngestedRootFile2}
import com.overviewdocs.util.{TempFiles,Textify}

class File2Writer(
  database: Database,
  blobStorage: BlobStorage,
  maxNTextChars: Int
) {
  import database.api._

  private val createChildCompiled = (File2s.map(f => (
    f.rootFile2Id,
    f.parentFile2Id,
    f.indexInParent,
    f.filename,
    f.contentType,
    f.languageCode,
    f.metadata,
    f.pipelineOptions,
    f.blobSha1,
    f.createdAt
  )) returning File2s)

  def createChild(
    parentFile2: WrittenFile2,
    indexInParent: Int,
    filename: String,
    contentType: String,
    languageCode: String,
    metadata: File2.Metadata,
    pipelineOptions: File2.PipelineOptions
  )(implicit ec: ExecutionContext): Future[CreatedFile2] = {
    for {
      file2 <- database.run(createChildCompiled.+=((
        parentFile2.rootId.orElse(Some(parentFile2.id)),
        Some(parentFile2.id),
        indexInParent,
        filename,
        contentType,
        languageCode,
        metadata,
        pipelineOptions,
        Array[Byte](),
        Instant.now()
      )))
    } yield CreatedFile2(
      file2.id,
      parentFile2.documentSetId,
      file2.rootFile2Id,
      file2.parentFile2Id,
      file2.indexInParent,
      file2.filename,
      file2.contentType,
      file2.languageCode,
      file2.metadata,
      file2.pipelineOptions,
      None,
      false,
      None,
      false
    )
  }

  private val deleteCompiled = Compiled { (id: Rep[Long]) =>
    File2s.filter(_.id === id)
  }

  def delete(
    file2: CreatedFile2
  )(implicit ec: ExecutionContext): Future[Unit] = {
    // Icky RACE: if we crash mid-delete, then the database will hold on to
    // BlobStorage references that no longer exist. We should guard against
    // this by adding a "deleting" flag.
    for {
      _ <- if (file2.ownsBlob) { blobStorage.delete(file2.blobOpt.get.location) } else { Future.unit }
      _ <- if (file2.ownsThumbnail) { blobStorage.delete(file2.thumbnailLocationOpt.get) } else { Future.unit }
      _ <- database.delete(deleteCompiled(file2.id))
    } yield ()
  }

  private val writeBlobCompiled = Compiled { (id: Rep[Long]) =>
    File2s
      .filter(_.id === id)
      .map(f => (f.blobLocation, f.blobNBytes, f.blobSha1))
  }

  def writeBlob(
    file2: CreatedFile2,
    blob: Source[ByteString, _]
  )(implicit ec: ExecutionContext, mat: Materializer): Future[CreatedFile2] = {
    file2.blobOpt match {
      case Some(_) => {
        // There is already a blob written on this File2. Assume the input
        // blob is identical -- and ignore it.
        //
        // We need to consume it, though: otherwise, the stream will stall.
        for {
          _ <- blob.runWith(Sink.ignore)
        } yield file2
      }
      case None => {
        for {
          ref <- createBlobStorageRef(blob)
          _ <- database.run(writeBlobCompiled(file2.id).update((Some(ref.location), Some(ref.nBytes), ref.sha1)))
        } yield file2.copy(blobOpt=Some(ref), ownsBlob=true)
      }
    }
  }

  def writeBlobStorageRef(
    file2: CreatedFile2,
    ref: BlobStorageRefWithSha1
  )(implicit ec: ExecutionContext): Future[CreatedFile2] = {
    file2.blobOpt match {
      case Some(_) => {
        // There is already a blob written on this File2. Assume the input
        // blob is identical -- and ignore it.
        Future.successful(file2)
      }
      case None => {
        for {
          _ <- database.run(writeBlobCompiled(file2.id).update((Some(ref.location), Some(ref.nBytes), ref.sha1)))
        } yield file2.copy(blobOpt=Some(ref), ownsBlob=false)
      }
    }
  }

  private val writeThumbnailCompiled = Compiled { (id: Rep[Long]) =>
    File2s
      .filter(_.id === id)
      .map(f => (f.thumbnailBlobLocation, f.thumbnailBlobNBytes, f.thumbnailContentType))
  }

  def writeThumbnail(
    file2: CreatedFile2,
    contentType: String,
    data: Source[ByteString, _]
  )(implicit ec: ExecutionContext, mat: Materializer): Future[CreatedFile2] = {
    file2.thumbnailLocationOpt match {
      case Some(_) => {
        // There is already a thumbnail written on this File2. Assume the input
        // blob is identical -- and ignore it.
        //
        // We need to consume it, though: otherwise, the stream will stall.
        for {
          _ <- data.runWith(Sink.ignore)
        } yield file2
      }
      case None => {
        for {
          ref <- createBlobStorageRef(data)
          _ <- database.run(writeThumbnailCompiled(file2.id).update((Some(ref.location), Some(ref.nBytes), Some(contentType))))
        } yield file2.copy(ownsThumbnail=true, thumbnailLocationOpt=Some(ref.location))
      }
    }
  }

  private val writeTextCompiled = Compiled { (id: Rep[Long]) =>
    File2s
      .filter(_.id === id)
      .map(_.text)
  }

  def writeText(
    file2: CreatedFile2,
    textUtf8: Source[ByteString, _]
  )(implicit ec: ExecutionContext, mat: Materializer): Future[CreatedFile2] = {
    for {
      text <- readText(textUtf8)
      _ <- database.run(writeTextCompiled(file2.id).update(Some(text)))
    } yield file2
  }

  private val setWrittenAtCompiled = Compiled { (id: Rep[Long]) =>
    File2s
      .filter(_.id === id)
      .map(_.writtenAt)
  }

  def setWritten(
    file2: CreatedFile2
  )(implicit ec: ExecutionContext): Future[WrittenFile2] = {
    val ret = file2.asWrittenFile2Opt.get

    for {
      _ <- database.run(setWrittenAtCompiled(file2.id).update(Some(Instant.now)))
    } yield ret
  }

  private val setProcessedCompiled = Compiled { (id: Rep[Long]) =>
    File2s
      .filter(_.id === id)
      .map(f => (f.processedAt, f.nChildren, f.processingError))
  }

  def setProcessed(
    file2: WrittenFile2,
    nChildren: Int,
    processingError: Option[String]
  )(implicit ec: ExecutionContext): Future[ProcessedFile2] = {
    for {
      _ <- database.run(setProcessedCompiled(file2.id).update((Some(Instant.now), Some(nChildren), processingError)))
    } yield ProcessedFile2(
      file2.id,
      file2.documentSetId,
      file2.parentId,
      nChildren,
      0
    )
  }

  private def readText(
    utf8ByteSource: Source[ByteString, _]
  )(implicit ec: ExecutionContext, mat: Materializer): Future[String] = {
    for {
      utf8ByteStrings <- utf8ByteSource.runWith(Sink.seq)
    } yield {
      val utf8Bytes = utf8ByteStrings.fold(ByteString.empty)(_ ++ _)
      val text = Textify(utf8Bytes.toArray, UTF_8)
      val truncated = Textify.truncateToNChars(text, maxNTextChars)
      truncated
    }
  }

  private def createBlobStorageRef(
    bytes: Source[ByteString, _]
  )(implicit ec: ExecutionContext, mat: Materializer): Future[BlobStorageRefWithSha1] = {
    val digestSink: Sink[ByteString, Future[Array[Byte]]] = {
      val digest = MessageDigest.getInstance("SHA-1")
      Sink.foreach((byteString: ByteString) => digest.update(byteString.toArray))
        .mapMaterializedValue((done: Future[akka.Done]) => done.map(_ => digest.digest))
    }

    TempFiles.withTempFileWithSuffix("File2Writer", path => {
      val (sha1Future, ioResultFuture) = bytes
        .alsoToMat(digestSink)(Keep.right)
        .toMat(FileIO.toPath(path))(Keep.both)
        .run

      for {
        ioResult <- ioResultFuture
        status = ioResult.status.get // crash on I/O error, before uploading to BlobStorage
        sha1 <- sha1Future
        location <- blobStorage.create(BlobBucketId.FileView, path)
      } yield BlobStorageRefWithSha1(BlobStorageRef(location, ioResult.count.toInt), sha1)
    })
  }

  def ingestBatch(
    file2s: immutable.Seq[ProcessedFile2]
  )(implicit ec: ExecutionContext): Future[Unit] = {
    file2s
      .groupBy(_.documentSetId)
      .foldLeft(Future.unit)((last, these) => {
        val documentSetId = these._1
        val theseIds = these._2.map(_.id)
        last.flatMap(_ => ingestBatchWithSameDocumentSetId(documentSetId, theseIds))
      })
  }

  private def ingestBatchWithSameDocumentSetId(
    documentSetId: Long,
    file2Ids: immutable.Seq[Long]
  )(implicit ec: ExecutionContext): Future[Unit] = {
    val metadataText: String = {
      "REPLACE(CONVERT_FROM(file2.metadata_json_utf8, 'utf-8'), '\\u0000', '')"
    }

    // No transactions needed: we can re-run this SQL during resume, and it'll
    // no-op.
    database.runUnit(sqlu"""
      DO $$$$
      DECLARE
        in_document_set_id CONSTANT BIGINT := #${documentSetId};
        in_file2_ids CONSTANT BIGINT[] := ARRAY[#${file2Ids.mkString(",")}];
        in_created_at CONSTANT TIMESTAMP WITH TIME ZONE := NOW();
        last_document_id BIGINT;
      BEGIN
        SELECT COALESCE(MAX(id), (in_document_set_id << 32) - 1) FROM document WHERE "document_set_id" = in_document_set_id INTO last_document_id;

        -- Create all un-created documents
        --
        -- Any processed file2 with text and no children becomes a document
        INSERT INTO document (
          id, document_set_id, text, title, page_number, created_at,
          metadata_json_text, is_from_ocr, file2_id
        )
        SELECT
          last_document_id + row_number() OVER (ORDER BY id),
          in_document_set_id,
          file2.text,
          file2.filename,
          CASE json_typeof(#${metadataText}::JSON -> 'pageNumber')
            WHEN 'number' THEN (#${metadataText}::JSON ->> 'pageNumber')::NUMERIC::INT
            ELSE NULL
          END,
          in_created_at,
          #${metadataText},
          CASE json_typeof(#${metadataText}::JSON -> 'isFromOcr')
            WHEN 'boolean' THEN (#${metadataText}::JSON ->> 'isFromOcr') = 'true'
            ELSE false
          END,
          file2.id
        FROM file2
        WHERE file2.id = ANY (in_file2_ids)
          AND file2.ingested_at IS NULL
          AND file2.text IS NOT NULL
          AND file2.n_children = 0
          AND NOT EXISTS (SELECT 1 FROM document WHERE file2_id = file2.id);

        -- Create all un-created document_processing_errors
        --
        -- Any processed file2 with an error becomes a document_processing_error
        INSERT INTO document_processing_error (
          document_set_id, text_url, message, file2_id
        )
        SELECT
          in_document_set_id,
          '',
          file2.processing_error,
          file2.id
        FROM file2
        WHERE file2.id = ANY (in_file2_ids)
          AND file2.processing_error IS NOT NULL
          AND file2.ingested_at IS NULL
          AND NOT EXISTS (SELECT 1 FROM document_processing_error WHERE file2_id = file2.id);

        -- We've ingested all file2s: even the ones which became neither
        -- documents nor document_processing_errors. (Those are parents.)
        UPDATE file2
        SET ingested_at = in_created_at
        WHERE file2.id = ANY (in_file2_ids)
          AND file2.ingested_at IS NULL;
      END$$$$
    """)
  }
}

object File2Writer {
  lazy val singleton = {
    val config = ConfigFactory.load
    new File2Writer(
      Database(),
      BlobStorage,
      config.getInt("max_n_chars_per_document")
    )
  }
}
