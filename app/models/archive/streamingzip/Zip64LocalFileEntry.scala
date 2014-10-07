package models.archive.streamingzip

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.SequenceInputStream
import java.nio.ByteOrder._
import java.nio.charset.StandardCharsets
import java.util.Calendar
import scala.collection.JavaConverters._
import models.archive.ArchiveEntry
import models.archive.streamingzip.HexByteString._
import java.util.zip.CheckedInputStream
import java.util.zip.CRC32

/**
 * In order to indicate that the entry is in Zip64 format, size and compressedSize values are -1, and a Zip64
 * Extra Field is added. The values in the Extra Field are set to `0`, since they are actually found in the data
 * descriptor.
 */
class Zip64LocalFileEntry(fileName: String, fileSize: Long, data: InputStream)
    extends LocalFileEntry(fileName, fileSize, data) {

  override protected val extractorVersion = useZip64Format
  override protected val compressedSize = unused
  override protected val uncompressedSize = unused
  
  private val extraFieldDataSize = localFileHeaderExtraField - 4
  override protected val extraFieldLength = localFileHeaderExtraField
  override protected val extraField: Array[Byte] = writeShort(zip64ExtraFieldTag) ++
    writeShort(extraFieldDataSize) ++
    writeLong(empty) ++
    writeLong(empty)
}

object Zip64LocalFileEntry {
  def apply(entry: ArchiveEntry): Zip64LocalFileEntry =
    new Zip64LocalFileEntry(entry.name, entry.size, entry.data)
}