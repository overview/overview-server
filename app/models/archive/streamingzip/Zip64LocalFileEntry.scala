package models.archive.streamingzip

import models.archive.CRCInputStream
import models.archive.ArchiveEntry
import models.archive.streamingzip.HexByteString._
import java.nio.charset.StandardCharsets
import java.io.InputStream
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder._
import java.util.Calendar
import java.io.SequenceInputStream
import scala.collection.JavaConverters._

/**
 * A Local File Entry in ZIP 64 format. A Data Descriptor is used so that the CRC32 can be computed
 * as the file data is read.
 * @param offset Is the offset in the ZIP stream of the Local File Entry
 * @param data The file content. No compression is performed.
 */
case class Zip64LocalFileEntry(fileName: String, fileSize: Long, data: CRCInputStream) extends LittleEndianWriter {
  private val HeaderSize = 30
  private val DataDescriptorSize = 24
  private val unused: Int = -1
  private val empty = 0
  
  val signature: Int = 0x04034b50
  val extractorVersion: Short = 10
  val flags: Short = 0x0808 // bit 3: Use Data Descriptor, bit 11: Use UTF-8
  val compression: Short = 0
  val fileNameLength: Short = fileNameSize
  def crc32 = data.crc32
  val timeStamp = DosDate(Calendar.getInstance())

  val dataDescriptorSignature = 0x07084b50
  
  def size: Long = HeaderSize + fileNameSize + fileSize + DataDescriptorSize

  val stream: InputStream = 
    new SequenceInputStream(Iterator(
        headerStream, 
        fileNameStream, 
        data,
        dataDescriptorStream).asJavaEnumeration)
  
  
  

  private def fileNameSize = fileName.getBytes(StandardCharsets.UTF_8).size.toShort

  private def headerStream: InputStream = 
    new ByteArrayInputStream(
      writeInt(signature) ++
        writeShort(extractorVersion) ++
        writeShort(flags) ++
        writeShort(compression) ++
        writeShort(timeStamp.time.toShort) ++
        writeShort(timeStamp.date.toShort) ++
        writeInt(empty) ++
        writeInt(unused) ++
        writeInt(unused) ++
        writeShort(fileNameLength) ++
        writeShort(empty.toShort))

  
  private def fileNameStream: InputStream = new ByteArrayInputStream(fileName.getBytes(StandardCharsets.UTF_8))
    
  private def dataDescriptorStream: InputStream = new LazyByteArrayInputStream(
    writeInt(dataDescriptorSignature) ++
    writeInt(crc32.toInt) ++
    writeLong(fileSize) ++
    writeLong(fileSize)
  )
}

object Zip64LocalFileEntry {
  def apply(entry: ArchiveEntry): Zip64LocalFileEntry =
    Zip64LocalFileEntry(entry.name, entry.size, entry.data)
}