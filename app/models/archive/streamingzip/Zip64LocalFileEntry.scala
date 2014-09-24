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

case class Zip64LocalFileEntry(fileName: String, fileSize: Long, offset: Long, data: CRCInputStream) {
  private val HeaderSize = 30
  private val ExtraFieldSize = 32
  private val DataDescriptorSize = 24
  private val unused: Int = -1
  private val unknown = 0
  
  val signature: Int = 0x04034b50
  val extractorVersion: Short = 10
  val flags: Short = 0x0808 // bit 3: Use Data Descriptor, bit 11: Use UTF-8
  val compression: Short = 0
  val fileNameLength: Short = fileNameSize
  def crc32 = data.crc32

  val dataDescriptorSignature = 0x07084b50
  
  def size: Long = HeaderSize + fileNameSize + ExtraFieldSize + fileSize + DataDescriptorSize

  val stream: InputStream = 
    new SequenceInputStream(Iterator(
        headerStream, 
        fileNameStream, 
        data,
        dataDescriptorStream).asJavaEnumeration)
  
  
  

  private def fileNameSize = fileName.getBytes(StandardCharsets.UTF_8).size.toShort

  private def headerStream: InputStream = {
    val now = DosDate(Calendar.getInstance())
    
    new ByteArrayInputStream(
      writeInt(signature) ++
        writeShort(extractorVersion) ++
        writeShort(flags) ++
        writeShort(compression) ++
        writeShort(now.time.toShort) ++
        writeShort(now.date.toShort) ++
        writeInt(unknown) ++
        writeInt(unused) ++
        writeInt(unused) ++
        writeShort(fileNameLength) ++
        writeShort(ExtraFieldSize.toShort))
  }
  
  private def fileNameStream: InputStream = new ByteArrayInputStream(fileName.getBytes(StandardCharsets.UTF_8))
    
  private def dataDescriptorStream: InputStream = new LazyByteArrayInputStream(
    writeInt(dataDescriptorSignature) ++
    writeInt(crc32.toInt) ++
    writeLong(fileSize) ++
    writeLong(fileSize)
  )
  
  private def writeLong(value: Long): Array[Byte] = {
    val byteBuffer = ByteBuffer.allocate(8).order(LITTLE_ENDIAN)
    byteBuffer.putLong(value).array()
  }
  
  private def writeInt(value: Int): Array[Byte] = {
    val byteBuffer = ByteBuffer.allocate(4).order(LITTLE_ENDIAN)
    byteBuffer.putInt(value).array()
  }

  private def writeShort(value: Short): Array[Byte] = {
    val byteBuffer = ByteBuffer.allocate(2).order(LITTLE_ENDIAN)
    byteBuffer.putShort(value).array()
  }

}

object Zip64LocalFileEntry {
  def apply(entry: ArchiveEntry, offset: Long): Zip64LocalFileEntry =
    Zip64LocalFileEntry(entry.name, entry.size, offset, entry.data)
}