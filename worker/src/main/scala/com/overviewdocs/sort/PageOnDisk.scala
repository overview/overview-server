package com.overviewdocs.sort

import akka.stream.scaladsl.{FileIO,Framing,Source}
import akka.stream.Materializer
import akka.util.ByteString
import java.nio.file.{Files,Path}
import java.nio.{ByteBuffer,ByteOrder}
import scala.collection.immutable
import scala.concurrent.{ExecutionContext,Future,blocking}
import scala.util.{Success,Failure}

/** A page of Records all written to disk.
  *
  * On disk, the Records take a bit less space than in memory. The file format
  * is simply a sequence of
  * `(id[4], canonicalPosition[4], nBytes[4], collationKey[nBytes])`
  */
case class PageOnDisk(
  nRecords: Int,
  path: Path
) {
  /** Reads and deletes this sorted page of records.
    *
    * By the time the materialized value completes, the file will be deleted
    * from disk.
    *
    * @param blockingEc ExecutionContext for blocking I/O
    */
  def toSourceDestructive(implicit blockingEc: ExecutionContext): Source[Record, Future[Unit]] = {
    FileIO.fromPath(path)
      .watchTermination() { (_, done) =>
        done.transformWith(_ => Future(blocking(Files.delete(path))))
      }
      .via(Framing.lengthField(4, 8, 12 + PageOnDisk.maxNBytesOnDiskPerRecord, PageOnDisk.byteOrder))
      .map(PageOnDisk.byteStringToRecord _)
  }
}

object PageOnDisk {
  private val byteOrder = ByteOrder.nativeOrder
  private[sort] val maxNBytesOnDiskPerRecord = 1024 * 1024 // truncate anything longer

  private def byteStringToRecord(byteString: ByteString): Record = {
    val buf = byteString.asByteBuffer
    buf.order(ByteOrder.nativeOrder)
    val id = buf.getInt
    val canonicalPosition = buf.getInt
    val collationKeyLength = buf.getInt
    val collationKey = new Array[Byte](collationKeyLength)
    buf.get(collationKey)
    Record(id, canonicalPosition, collationKey)
  }

  private def recordToByteString(record: Record): ByteString = {
    val buf = ByteString.newBuilder
    buf.sizeHint(12 + record.collationKey.size)
    var collationKey = if (record.collationKey.size < maxNBytesOnDiskPerRecord - 12) {
      record.collationKey
    } else {
      record.collationKey.slice(0, maxNBytesOnDiskPerRecord - 12)
    }

    buf
      .putInt(record.id)(byteOrder)
      .putInt(record.canonicalPosition)(byteOrder)
      .putInt(collationKey.size)(byteOrder)
      .putBytes(collationKey)
      .result
  }

  private def streamRecordsToPath(records: Source[Record, _], path: Path)(implicit mat: Materializer, ec: ExecutionContext): Future[Unit] = {
    records
      .map(recordToByteString _)
      .runWith(FileIO.toPath(path))
      .map(_ => ())
  }

  /** Writes already-sorted records to disk, creating a PageOnDisk.
    *
    * @param directory Parent directory of file to create.
    * @param records Records to write to the file.
    */
  def create(directory: Path, recordSource: RecordSource)(implicit mat: Materializer, blockingEc: ExecutionContext): Future[PageOnDisk] = {
    for {
      path <- Future(blocking(Files.createTempFile(directory, null, null)))
      _ <- streamRecordsToPath(recordSource.records, path)
    } yield PageOnDisk(recordSource.nRecords, path)
  }

  /** Sorts and writes records to disk, creating a PageOnDisk.
    *
    * @param directory Parent directory of file to create.
    * @param records Records to sort and then write to the file.
    */
  def sortAndCreate(directory: Path, records: immutable.Seq[Record])(implicit mat: Materializer, blockingEc: ExecutionContext): Future[PageOnDisk] = {
    create(directory, RecordSource(
      records.size,
      Source(records.sorted).mapMaterializedValue(_ => Future.unit)
    ))
  }
}
