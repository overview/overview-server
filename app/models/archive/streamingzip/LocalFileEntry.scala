package models.archive.streamingzip

import java.io.InputStream
import java.io.ByteArrayInputStream
import java.util.Calendar
import java.nio.charset.StandardCharsets
import java.io.SequenceInputStream
import scala.collection.JavaConverters._
import java.util.zip.CheckedInputStream
import java.util.zip.CRC32

/**
 * A Local File Entry in a ZIP archive. A Data Descriptor is used so that the CRC32 can be computed
 * as the file data is read. Entries are streamed, so size and CRC32 values are stored in the data descriptor.
 * @param offset Is the offset in the ZIP stream of the Local File Entry
 * @param data The file content. No compression is performed.
 */

class LocalFileEntry(val fileName: String, val fileSize: Long, data: InputStream) extends LittleEndianWriter with ZipFormat
    with ZipFormatSize {

  protected val extractorVersion = defaultVersion
  protected val flags = useDataDescriptor | useUTF8
  protected val timeStamp = DosDate(Calendar.getInstance())
  protected val fileNameLength = fileName.getBytes(StandardCharsets.UTF_8).size.toShort
  protected val extraFieldLength = 0
  protected val compressedSize = empty
  protected val uncompressedSize = empty
  
  protected val extraField: Array[Byte] = Array.empty

  protected val checkedData = new CheckedInputStream(data, new CRC32)
  
  def crc32: Int = checkedData.getChecksum.getValue.toInt
  
  def size: Long = localFileHeader + fileNameLength + extraFieldLength + fileSize + dataDescriptor

  
  lazy val stream: InputStream = new SequenceInputStream(Iterator(
    headerStream,
    fileNameStream,
    extraFieldStream,
    checkedData,
    dataDescriptorStream).asJavaEnumeration)

  private def headerStream: InputStream =
    new ByteArrayInputStream(
      writeInt(localFileEntrySignature) ++
        writeShort(extractorVersion) ++
        writeShort(flags) ++
        writeShort(noCompression) ++
        writeShort(timeStamp.time.toShort) ++
        writeShort(timeStamp.date.toShort) ++
        writeInt(empty) ++
        writeInt(compressedSize) ++
        writeInt(uncompressedSize) ++
        writeShort(fileNameLength) ++
        writeShort(extraFieldLength))

  private def fileNameStream: InputStream = new ByteArrayInputStream(fileName.getBytes(StandardCharsets.UTF_8))

  private def extraFieldStream = new ByteArrayInputStream(extraField)

  private def dataDescriptorStream: InputStream = new LazyByteArrayInputStream(
    writeInt(dataDescriptorSignature) ++
      writeInt(crc32) ++
      writeLong(fileSize) ++
      writeLong(fileSize))

}