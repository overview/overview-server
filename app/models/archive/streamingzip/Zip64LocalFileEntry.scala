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
 * A Local File Entry in ZIP 64 format. A Data Descriptor is used so that the CRC32 can be computed
 * as the file data is read. Entries are streamed, so size and CRC32 values are stored in the data descriptor.
 * In order to indicate that the entry is in Zip64 format, size and compressedSize values are -1, and a Zip64
 * Extra Field is added. The values in the Extra Field are set to `0`, since they are actually found in the data
 * descriptor.
 * @param offset Is the offset in the ZIP stream of the Local File Entry
 * @param data The file content. No compression is performed.
 */
case class Zip64LocalFileEntry(fileName: String, fileSize: Long, data: InputStream) extends LittleEndianWriter with ZipFormat with ZipFormatSize {

  val extractorVersion = useZip64Format
  val flags = useDataDescriptor | useUTF8
  val fileNameLength: Short = fileNameSize

  val timeStamp = DosDate(Calendar.getInstance())
  val extraFieldDataSize = localFileHeaderExtraField - 4

  private val checkedData = new CheckedInputStream(data, new CRC32)
  def crc32: Int = checkedData.getChecksum.getValue.toInt

  def size: Long = localFileHeader + fileNameSize + localFileHeaderExtraField + fileSize + dataDescriptor

  val stream: InputStream =
    new SequenceInputStream(Iterator(
      headerStream,
      fileNameStream,
      extraFieldStream,
      checkedData,
      dataDescriptorStream).asJavaEnumeration)

  private def fileNameSize = fileName.getBytes(StandardCharsets.UTF_8).size.toShort

  private def headerStream: InputStream =
    new ByteArrayInputStream(
      writeInt(localFileEntrySignature) ++
        writeShort(extractorVersion) ++
        writeShort(flags) ++
        writeShort(noCompression) ++
        writeShort(timeStamp.time.toShort) ++
        writeShort(timeStamp.date.toShort) ++
        writeInt(empty) ++
        writeInt(unused) ++
        writeInt(unused) ++
        writeShort(fileNameLength) ++
        writeShort(localFileHeaderExtraField))

  private def fileNameStream: InputStream = new ByteArrayInputStream(fileName.getBytes(StandardCharsets.UTF_8))

  private def extraFieldStream = new ByteArrayInputStream(
    writeShort(zip64ExtraFieldTag) ++
      writeShort(extraFieldDataSize) ++
      writeLong(empty) ++
      writeLong(empty))

  private def dataDescriptorStream: InputStream = new LazyByteArrayInputStream(
    writeInt(dataDescriptorSignature) ++
      writeInt(crc32) ++
      writeLong(fileSize) ++
      writeLong(fileSize))
}

object Zip64LocalFileEntry {
  def apply(entry: ArchiveEntry): Zip64LocalFileEntry =
    Zip64LocalFileEntry(entry.name, entry.size, entry.data)
}