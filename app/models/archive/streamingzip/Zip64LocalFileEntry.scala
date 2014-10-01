package models.archive.streamingzip

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.SequenceInputStream
import java.nio.ByteOrder._
import java.nio.charset.StandardCharsets
import java.util.Calendar
import scala.collection.JavaConverters._
import models.archive.ArchiveEntry
import models.archive.CRCInputStream
import models.archive.streamingzip.HexByteString._

/**
 * A Local File Entry in ZIP 64 format. A Data Descriptor is used so that the CRC32 can be computed
 * as the file data is read. Entries are streamed, so size and CRC32 values are stored in the data descriptor.
 * In order to indicate that the entry is in Zip64 format, size and compressedSize values are -1, and a Zip64
 * Extra Field is added. The values in the Extra Field are set to `0`, since they are actually found in the data
 * descriptor.
 * @param offset Is the offset in the ZIP stream of the Local File Entry
 * @param data The file content. No compression is performed.
 */
case class Zip64LocalFileEntry(fileName: String, fileSize: Long, data: CRCInputStream) extends LittleEndianWriter {
  private val HeaderSize = 30
  private val DataDescriptorSize = 24
  private val Zip64Tag: Short = 1
  private val unused: Int = -1
  private val empty = 0
  
  val signature: Int = 0x04034b50
  val extractorVersion: Short = 45
  val flags: Short = 0x0808 // bit 3: Use Data Descriptor, bit 11: Use UTF-8
  val compression: Short = 0
  val fileNameLength: Short = fileNameSize
  def crc32 = data.crc32
  val timeStamp = DosDate(Calendar.getInstance())
  val extraFieldSize: Short = 20
  val extraFieldDataSize: Short = (extraFieldSize - 4).toShort
  
  val dataDescriptorSignature = 0x08074b50
  
  def size: Long = HeaderSize + fileNameSize + extraFieldSize + fileSize + DataDescriptorSize

  val stream: InputStream = 
    new SequenceInputStream(Iterator(
        headerStream, 
        fileNameStream, 
        extraFieldStream,
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
        writeShort(extraFieldSize))

  
  private def fileNameStream: InputStream = new ByteArrayInputStream(fileName.getBytes(StandardCharsets.UTF_8))
    
  private def extraFieldStream = new ByteArrayInputStream(
    writeShort(Zip64Tag) ++
    writeShort(extraFieldDataSize) ++
    writeLong(empty) ++ 
    writeLong(empty)
  )
  
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