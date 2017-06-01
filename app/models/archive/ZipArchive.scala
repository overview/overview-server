package models.archive

import akka.stream.scaladsl.{Sink,Source}
import akka.util.ByteString
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.iteratee.{Enumerator,Iteratee}
import java.nio.{ByteBuffer,ByteOrder}
import java.util.HashSet
import java.util.zip.CRC32
import scala.collection.immutable
import scala.collection.mutable
import scala.concurrent.Future

import models.ArchiveEntry
import controllers.backend.ArchiveEntryBackend // TODO rename controllers.backend => models.backend

/** All the information you need to produce a zipfile.
  */
class ZipArchive(
  documentSetId: Long,
  archiveEntriesWithDuplicates: Seq[ArchiveEntry],
  backend: ArchiveEntryBackend
) {
  // Constants and format from https://pkware.cachefly.net/webdocs/casestudies/APPNOTE.TXT
  private val CentralDirectoryHeaderSize: Int = 46
  private val EndOfCentralDirectorySize: Int = 22
  private val LocalFileHeaderSize: Int = 30

  private val NoCompression: Short = 0
  private val LocalFileEntrySignature: Int =  0x04034b50
  private val CentralFileHeaderSignature: Int = 0x02014b50
  private val EndOfCentralDirectoryRecordSignature: Int = 0x06054b50
  private val VersionMadeByUnix: Short = 3
  private val VersionNeededToExtract: Short = 45 // 4.5 ... we use no encryption/compression, but we haven't tested on old zip versions
  private val Flags: Short = 0x0800 // "use UTF-8"
  private val ExternalFileAttributes: Int = 0x81800000 // unix: "-rw-------"

  /** Uniquely-named archive entries.
    *
    * We don't have a use case for duplicate-named entries. The simplest
    * (fastest) implementation removes duplicates: that way we don't need to
    * rename files, so we know how many bytes each filename will contain, so we
    * can calculate the entire size of the output zipfile with minimal math.
    */
  val archiveEntries: immutable.Seq[ArchiveEntry] = {
    // Use WrappedArray[Byte] instead of Array[Byte] so hashCode() will work.
    // Use Java HashSet because it lets us set initial capacity. 0.75 is the HashMap default load factor.
    val usedFilenamesUtf8 = new HashSet[mutable.WrappedArray[Byte]](
      (archiveEntriesWithDuplicates.length.toFloat / 0.75).toInt,
      1
    )
    archiveEntriesWithDuplicates
      .filter(e => usedFilenamesUtf8.add(e.filenameUtf8)) // clever: Set.add() returns false on dup
      .to[immutable.Seq]
  }

  val size: Long = {
    var ret: Long = EndOfCentralDirectorySize + archiveEntries.length * (CentralDirectoryHeaderSize + LocalFileHeaderSize)
    for (entry <- archiveEntries) {
      ret += 2 * entry.filenameUtf8.length + entry.nBytes
    }
    ret
  }

  /** CRCs of all files we've streamed.
    *
    * We show CRCs at the beginning of each file and in the Central Directory
    * at the end of the zipfile.
    */
  private var crcs: mutable.Map[Long,Int] = mutable.Map.empty

  private val timestamp = DosDate.now

  def stream: Source[ByteString, akka.NotUsed] = {
    // https://github.com/akka/akka/issues/18683
    // https://github.com/akka/akka/issues/23044
    // streamCentralDirectory() depends on crcs being full. And by the time
    // we're streaming the central directory into our output, they are. But
    // Akka's "Stream.concat" erroneously fetches the first element before
    // realizing it's supposed to wait -- even with Source.lazily().
    //
    // Our workaround: assume Akka will only fetch a single element. Compute
    // that CRC before continuing.

    val loadFirstCrcForAkkaBug18683: Future[Unit] = archiveEntries.headOption match {
      case None => Future.successful(())
      case Some(archiveEntry) => computeCrc(archiveEntry).map(crc => crcs(archiveEntry.documentId) = crc)
    }

    val futureSource = loadFirstCrcForAkkaBug18683.map(_ =>
      streamLocalFiles
        .concat(Source.lazily({ () =>
          // wait lazily for crcs to be computed
          streamCentralDirectory
            .concat(Source.single(ByteString(endOfCentralDirectory)))
        }))
    )

    Source.fromFutureSource(futureSource).mapMaterializedValue(_ => akka.NotUsed)
  }

  private def streamLocalFiles: Source[ByteString, akka.NotUsed] = {
    Source(archiveEntries).flatMapConcat(streamLocalFile _)
  }

  /** Stream an ArchiveEntry, and set this.crcs(archiveEntry.documentId) as a
    * side-effect.
    */
  private def streamLocalFile(archiveEntry: ArchiveEntry): Source[ByteString, akka.NotUsed] = {
    val future = for {
      crc <- computeCrc(archiveEntry)
    } yield {
      crcs(archiveEntry.documentId) = crc
      val headerBytes = ByteString(localFileHeader(archiveEntry))

      Source.single(headerBytes)
        .concat(backend.streamBytes(documentSetId, archiveEntry.documentId))
    }

    Source.fromFutureSource(future)
      .mapMaterializedValue( _ =>  akka.NotUsed)
  }

  private def localFileHeader(archiveEntry: ArchiveEntry): Array[Byte] = {
    val buf = ByteBuffer
      .allocate(LocalFileHeaderSize + archiveEntry.filenameUtf8.length)
      .order(ByteOrder.LITTLE_ENDIAN)

    assert(archiveEntry.nBytes.toInt.toLong == archiveEntry.nBytes) // TODO use Zip64

    // 4.3.7  Local file header
    buf
      .putInt(LocalFileEntrySignature)
      .putShort(VersionNeededToExtract)
      .putShort(Flags)
      .putShort(NoCompression)
      .putShort(timestamp.time)
      .putShort(timestamp.date)
      .putInt(crcs(archiveEntry.documentId))
      .putInt(archiveEntry.nBytes.toInt) // compressed size
      .putInt(archiveEntry.nBytes.toInt) // uncompressed size
      .putShort(archiveEntry.filenameUtf8.length.toShort)
      .putShort(0)
      .put(archiveEntry.filenameUtf8)

    assert(buf.position == buf.limit)
    buf.array
  }

  private def streamCentralDirectory: Source[ByteString, akka.NotUsed] = {
    streamCentralDirectoryFileHeaders
  }

  private def streamCentralDirectoryFileHeaders: Source[ByteString, akka.NotUsed] = {
    // Iterate in the same order as we streamed the files, so we can write all
    // the offsets.
    val startIterator = archiveEntries.iterator
    Source.unfold((startIterator, 0)) { case (iterator: Iterator[ArchiveEntry], curOffset: Int) =>
      if (iterator.hasNext) {
        val archiveEntry = iterator.next
        val localFileSize = LocalFileHeaderSize + archiveEntry.filenameUtf8.length + archiveEntry.nBytes
        val bytes = ByteString(centralDirectoryFileHeader(archiveEntry, curOffset))
        Some(((iterator, curOffset + localFileSize.toInt), bytes))
      } else {
        None
      }
    }
  }

  private def centralDirectoryFileHeader(archiveEntry: ArchiveEntry, localFileOffset: Int): Array[Byte] = {
    val buf = ByteBuffer
      .allocate(CentralDirectoryHeaderSize + archiveEntry.filenameUtf8.length)
      .order(ByteOrder.LITTLE_ENDIAN)

    // 4.3.12 Central directory structure
    buf
      .putInt(CentralFileHeaderSignature)
      .putShort(VersionMadeByUnix)
      .putShort(VersionNeededToExtract)
      .putShort(Flags)
      .putShort(NoCompression)
      .putShort(timestamp.time)
      .putShort(timestamp.date)
      .putInt(crcs(archiveEntry.documentId))
      .putInt(archiveEntry.nBytes.toInt) // compressed size
      .putInt(archiveEntry.nBytes.toInt) // uncompressed size
      .putShort(archiveEntry.filenameUtf8.length.toShort)
      .putShort(0) // extra field length
      .putShort(0) // file comment length
      .putShort(0) // disk number
      .putShort(0) // internal file attributes
      .putInt(ExternalFileAttributes)
      .putInt(localFileOffset)
      .put(archiveEntry.filenameUtf8)

    assert(buf.position == buf.limit)
    buf.array
  }

  private def endOfCentralDirectory: Array[Byte] = {
    val buf = ByteBuffer.allocate(EndOfCentralDirectorySize).order(ByteOrder.LITTLE_ENDIAN)

    var centralDirectoryStart: Int = archiveEntries.length * LocalFileHeaderSize
    var centralDirectorySize: Int = archiveEntries.length * CentralDirectoryHeaderSize
    for (entry <- archiveEntries) {
      centralDirectoryStart += entry.filenameUtf8.length + entry.nBytes.toInt
      centralDirectorySize += entry.filenameUtf8.length
    }

    // 4.3.16  End of central directory record
    buf
      .putInt(EndOfCentralDirectoryRecordSignature)
      .putShort(0) // disk number
      .putShort(0) // disk number of start of central directory
      .putShort(archiveEntries.length.toShort) // number of entries in central directory on this disk
      .putShort(archiveEntries.length.toShort) // number of entries in central directory
      .putInt(centralDirectorySize)
      .putInt(centralDirectoryStart)
      .putShort(0) // comment size

    assert(buf.position == buf.limit)
    buf.array
  }

  /** Compute the CRC of an ArchiveEntry.
    *
    * TODO cache smaller documents, so we don't stream them twice. (We already
    * know the size from archiveEntry.) Or find some other way to avoid writing
    * the CRC32 before the file contents (cross-platform).
    */
  private def computeCrc(archiveEntry: ArchiveEntry): Future[Int] = {
    val crc32 = new CRC32()
    val source: Source[ByteString, _] = backend.streamBytes(documentSetId, archiveEntry.documentId)
    val sink = Sink.foreach { (bytes: ByteString) => crc32.update(bytes.toArray) }
    for {
      _ <- source.runWith(sink)(play.api.Play.current.materializer)
    } yield crc32.getValue.toInt
  }
}
