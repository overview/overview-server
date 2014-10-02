package models.archive.streamingzip

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.SequenceInputStream
import java.nio.charset.StandardCharsets
import scala.collection.JavaConverters._
import java.util.zip.CheckedInputStream

/**
 * Central Directory File Headers in Zip64 format
 */
class Zip64CentralFileHeader(fileName: String, fileSize: Long, offset: Long, timeStamp: DosDate, crcFunction: => Int) extends LittleEndianWriter with ZipFormat {
  private val DataSize = 46
  private val ExtraFieldSize = 28

  private val fileNameSize = fileNameBytes.length

  val versionMadeBy = unix | zipSpecification
  
  val flags: Short = useUTF8
  val extractorVersion: Short = useZip64Format
  val extraFieldDataSize = ExtraFieldSize - 4
  

  def size: Int = DataSize + ExtraFieldSize + fileNameSize

  def stream: InputStream = new SequenceInputStream(Iterator(
    headerStream,
    fileNameStream,
    extraFieldStream).asJavaEnumeration)

  private def headerStream = new LazyByteArrayInputStream(
    writeInt(centralFileHeaderSignature) ++ 
      writeShort(versionMadeBy) ++
      writeShort(extractorVersion) ++
      writeShort(flags) ++
      writeShort(noCompression) ++
      writeShort(timeStamp.time.toShort) ++
      writeShort(timeStamp.date.toShort) ++
      writeInt(crcFunction) ++
      writeInt(unused) ++
      writeInt(unused) ++
      writeShort(fileNameSize.toShort) ++
      writeShort(ExtraFieldSize.toShort) ++
      writeShort(empty) ++
      writeShort(empty) ++
      writeShort(empty) ++
      writeInt(empty) ++
      writeInt(unused))

  private def fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8)
  private def fileNameStream: InputStream = new ByteArrayInputStream(fileNameBytes)

  private def extraFieldStream = new ByteArrayInputStream(
    writeShort(zip64ExtraFieldTag) ++
      writeShort(extraFieldDataSize.toShort) ++
      writeLong(fileSize) ++
      writeLong(fileSize) ++
      writeLong(offset) 
      )

}