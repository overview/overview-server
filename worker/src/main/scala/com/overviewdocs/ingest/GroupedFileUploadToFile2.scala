package com.overviewdocs.ingest

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.{Files,StandardOpenOption,Path}
import java.security.MessageDigest
import java.time.Instant
import scala.concurrent.{ExecutionContext,Future,blocking}

import com.overviewdocs.blobstorage.{BlobBucketId,BlobStorage}
import com.overviewdocs.database.{Database,LargeObject}
import com.overviewdocs.models.{BlobStorageRef,File2,FileGroup,GroupedFileUpload,StatefulFile2}
import com.overviewdocs.models.tables.{File2s,GroupedFileUploads}
import com.overviewdocs.util.{Logger,TempFiles}

/** "Converts" a GroupedFileUpload to a File2 by copying data.
  *
  * Performs these steps:
  *
  * 1. Set grouped_file_upload.file2_id to a newly-created File2
  * 2. Copy data to the File2
  * 3. Return the File2
  *
  * Resumes by selecting from grouped_file_upload.file2_id, if it is set.
  *
  * Understand: at the time of writing, users upload to a PostgreSQL
  * LargeObject. That's cruft: a user-input file upload should map exactly
  * to a File2. Once we fix user uploads to upload straight to BlobStorage,
  * we'll nix this class. That's why there's no progress reporting: even
  * though it currently takes time to copy data, in the future it will be
  * a no-op because the File2 will already be written during upload.
  */
class GroupedFileUploadToFile2(database: Database, blobStorage: BlobStorage) {
  private val logger = Logger.forClass(getClass)

  /** Returns the File2 corresponding to groupedFileUpload.
    *
    * The return value will be in the Written or Processed state.
    */
  def groupedFileUploadToFile2(
    fileGroup: FileGroup,
    groupedFileUpload: GroupedFileUpload
  )(implicit ec: ExecutionContext): Future[File2] = {
    val maybeStatefulFile2Future = groupedFileUpload.file2Id match {
      case Some(file2Id) => lookupFile2(file2Id).map(f => f.map(_.toStatefulFile2))
      case None => Future.successful(None)
    }

    maybeStatefulFile2Future.flatMap(_ match {
      case Some(StatefulFile2.Ingested(file2)) => Future.successful(file2)
      case Some(StatefulFile2.Processed(file2)) => Future.successful(file2)
      case Some(StatefulFile2.Written(file2)) => Future.successful(file2)
      case Some(StatefulFile2.Created(file2)) => writeFile2(groupedFileUpload, file2)
      case None => createFile2(fileGroup, groupedFileUpload).flatMap(file2 => writeFile2(groupedFileUpload, file2))
    })
  }

  /** Writes a File2 database row and updates groupedFileUpload's file2_id to
    * the database.
    *
    * The returned File2 will be in the Created state: you'll need to write to it.
    */
  private def createFile2(
    fileGroup: FileGroup,
    groupedFileUpload: GroupedFileUpload
  )(implicit ec: ExecutionContext): Future[File2] = {
    import database.api._

    // File2 creation is a bit tricky because rootFile2Id == file2Id, which is
    // a SEQUENCE nextval.
    val filename = groupedFileUpload.name
    val contentType = groupedFileUpload.contentType
    val languageCode = fileGroup.lang.getOrElse("en")
    val metadata = File2.Metadata(groupedFileUpload.documentMetadataJson.getOrElse(fileGroup.metadataJson))
    val pipelineOptions = File2.PipelineOptions(
      fileGroup.ocr.getOrElse(false),
      fileGroup.splitDocuments.getOrElse(false),
      Vector()
    )
    val createdAt=Instant.now()

    val action = (for {
      file2 <- GroupedFileUploadToFile2.file2Inserter += (
        0,
        filename,
        contentType,
        languageCode,
        metadata,
        pipelineOptions,
        Array[Byte](),
        createdAt
      )
      _ <- GroupedFileUploadToFile2.compiledGroupedFileUploadUpdater(groupedFileUpload.id)
        .update(Some(file2.id))
    } yield file2).transactionally

    database.run(action)
  }

  /** Transfers data from groupedFileUpload to file2's BlobStorage location. */
  private def writeFile2(
    groupedFileUpload: GroupedFileUpload,
    file2: File2
  )(implicit ec: ExecutionContext): Future[File2] = {
    import database.api._

    for {
      (location, nBytes, sha1) <- copyOidToBlobStorage(groupedFileUpload.contentsOid)
      writtenAt = Instant.now()
      _ <- database.run(
        GroupedFileUploadToFile2.compiledFile2Updater(file2.id)
          .update((Some(location), Some(nBytes), sha1, Some(writtenAt)))
      )
    } yield {
      file2.copy(
        blob=Some(BlobStorageRef(location, nBytes)),
        blobSha1=sha1,
        writtenAt=Some(writtenAt)
      )
    }
  }

  /** Looks up a File2 by ID.
    *
    * It may be Created, Written or Processed. In a race, it may be None.
    */
  private def lookupFile2(id: Long)(implicit ec: ExecutionContext): Future[Option[File2]] = {
    database.option(GroupedFileUploadToFile2.compiledFile2(id))
  }

  /** Fulfil the only purpose of this class.
    *
    * Returns: (location, nBytes, sha1)
    */
  private def copyOidToBlobStorage(oid: Long)(implicit ec: ExecutionContext): Future[(String,Int,Array[Byte])] = {
    TempFiles.withTempFileWithSuffix("grouped-file-upload-to-file2", tmpPath => {
      for {
        (nBytes, sha1) <- downloadLargeObjectAndCalculateSha1(oid, tmpPath)
        location <- blobStorage.create(BlobBucketId.FileContents, tmpPath)
      } yield (location, nBytes, sha1)
    })
  }

  private def downloadLargeObjectAndCalculateSha1(loid: Long, destination: Path)(implicit ec: ExecutionContext): Future[(Int, Array[Byte])] = {
    val CopyBufferSize = 5 * 1024 * 1024
    val loManager = database.largeObjectManager

    val digest = MessageDigest.getInstance("SHA-1")
    val channel = blocking { FileChannel.open(destination, StandardOpenOption.WRITE) }

    sealed trait State
    case class NeedRead(nBytesRead: Int) extends State
    case class NeedWrite(nBytesRead: Int, unwrittenBytes: ByteBuffer) extends State
    case class Done(nBytes: Int) extends State

    def readBlock(nBytesRead: Int): Future[State] = {
      val action = for {
        lo <- loManager.open(loid, LargeObject.Mode.Read)
        _ <- lo.seek(nBytesRead)
        bytes <- lo.read(CopyBufferSize)
      } yield {
        if (bytes.length == 0) {
          Done(nBytesRead)
        } else {
          digest.update(bytes)
          NeedWrite(nBytesRead + bytes.length, ByteBuffer.wrap(bytes))
        }
      }

      import database.api._
      database.run(action.transactionally)
    }

    def writeBlock(nBytesRead: Int, buf: ByteBuffer): Future[State] = {
      for {
        _ <- Future(blocking { channel.write(buf) })
      } yield {
        if (buf.hasRemaining) {
          NeedWrite(nBytesRead, buf)
        } else {
          NeedRead(nBytesRead)
        }
      }
    }

    def transferRemaining(state: State): Future[Int] = state match {
      case NeedRead(nBytesRead) => readBlock(nBytesRead).flatMap(transferRemaining _)
      case NeedWrite(nBytesRead, unwrittenBytes) => writeBlock(nBytesRead, unwrittenBytes).flatMap(transferRemaining _)
      case Done(nBytes) => Future.successful(nBytes)
    }

    for {
      nBytes <- transferRemaining(NeedRead(0))
      _ <- Future(blocking { channel.close })
    } yield {
      (nBytes, digest.digest())
    }
  }
}

object GroupedFileUploadToFile2 {
  import com.overviewdocs.database.Slick.api._

  private lazy val compiledFile2 = {
    Compiled { file2Id: Rep[Long] =>
      File2s.filter(_.id === file2Id)
    }
  }

  private lazy val compiledFile2Updater = {
    Compiled { file2Id: Rep[Long] =>
      File2s
        .filter(_.id === file2Id)
        .map(f => (f.blobLocation, f.blobNBytes, f.blobSha1, f.writtenAt))
    }
  }

  private lazy val compiledGroupedFileUploadUpdater = {
    Compiled { id: Rep[Long] =>
      GroupedFileUploads
        .filter(_.id === id)
        .map(_.file2Id)
    }
  }

  private val file2Inserter = (File2s.map(f => (
    // All non-NULL columns
    f.indexInParent,
    f.filename,
    f.contentType,
    f.languageCode,
    f.metadata,
    f.pipelineOptions,
    f.blobSha1,
    f.createdAt
  )) returning File2s)
}
