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
      None
    )
  }

  private val deleteFile2Compiled = Compiled { (id: Rep[Long]) =>
    File2s.filter(_.id === id)
  }

  def deleteFile2(
    file2: CreatedFile2
  )(implicit ec: ExecutionContext): Future[Unit] = {
    database.delete(deleteFile2Compiled(file2.id))
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
    // TODO no-op if already written, to avoid leaking blobs on resume
    for {
      ref <- createBlobStorageRef(blob)
      _ <- database.run(writeBlobCompiled(file2.id).update((Some(ref.location), Some(ref.nBytes), ref.sha1)))
    } yield file2.copy(blobOpt=Some(ref))
  }

  def writeBlobStorageRef(
    file2: CreatedFile2,
    ref: BlobStorageRefWithSha1
  )(implicit ec: ExecutionContext): Future[CreatedFile2] = {
    // TODO no-op if already written, to avoid leaking blobs on resume
    for {
      _ <- database.run(writeBlobCompiled(file2.id).update((Some(ref.location), Some(ref.nBytes), ref.sha1)))
    } yield file2.copy(blobOpt=Some(ref))
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
    // TODO no-op if already written, to avoid leaking blobs on resume
    for {
      ref <- createBlobStorageRef(data)
      _ <- database.run(writeThumbnailCompiled(file2.id).update((Some(ref.location), Some(ref.nBytes), Some(contentType))))
    } yield file2
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
        last.map(_ => ingestBatchWithSameDocumentSetId(documentSetId, theseIds))
      })
  }

  private def ingestBatchWithSameDocumentSetId(
    documentSetId: Long,
    file2Ids: immutable.Seq[Long]
  )(implicit ec: ExecutionContext): Future[Unit] = {
    ???
//    database.runUnit(sqlu"""
//      DO $$$$
//      DECLARE
//        document_set_id BIGINT := ${documentSetId};
//        file2_ids BIGINT[] := ARRAY[#${file2Ids.mkString(",")}];
//        created_at TIMESTAMP WITH TIME ZONE := NOW();
//        last_id BIGINT
//      BEGIN
//        SELECT MAX(id) FROM documents WHERE document_set_id = document_set_id INTO last_id;
//
//        INSERT INTO documents (
//          id, document_set_id, text, title, page_number, created_at,
//          metadata_json_text, is_from_ocr, file2_id
//        )
//        SELECT
//          last_id + row_number(),
//          document_set_id,
//          file2.text,
//          file2.filename,
//          created_at,
//          file2.metadata_json_utf8::VARCHAR,
//          file2.
//      END$$$$
//    """)
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
