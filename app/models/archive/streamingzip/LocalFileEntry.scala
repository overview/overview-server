package models.archive.streamingzip

import java.io.InputStream
import java.io.ByteArrayInputStream
import java.util.Calendar
import java.nio.charset.StandardCharsets
import java.io.SequenceInputStream
import scala.collection.JavaConverters._
import java.util.zip.CheckedInputStream
import java.util.zip.CRC32

case class LocalFileEntry(fileName: String, fileSize: Long, data: InputStream) extends LittleEndianWriter with ZipFormat
    with ZipFormatSize {

  protected val extractorVersion = defaultVersion
  protected val flags = useDataDescriptor | useUTF8
  protected val timeStamp = DosDate(Calendar.getInstance())
  protected val fileNameLength = fileName.getBytes(StandardCharsets.UTF_8).size.toShort

  protected val checkedData = new CheckedInputStream(data, new CRC32)
  def crc32: Int = checkedData.getChecksum.getValue.toInt
  def size: Long = localFileHeader + fileNameLength + fileSize + dataDescriptor

  val stream: InputStream = new SequenceInputStream(Iterator(
    headerStream,
    fileNameStream,
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
        writeInt(empty) ++
        writeInt(empty) ++
        writeShort(fileNameLength) ++
        writeShort(empty))

  private def fileNameStream: InputStream = new ByteArrayInputStream(fileName.getBytes(StandardCharsets.UTF_8))

  private def dataDescriptorStream: InputStream = new LazyByteArrayInputStream(
    writeInt(dataDescriptorSignature) ++
      writeInt(crc32) ++
      writeLong(fileSize) ++
      writeLong(fileSize))

}