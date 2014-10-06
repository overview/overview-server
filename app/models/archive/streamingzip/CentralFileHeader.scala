package models.archive.streamingzip

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.SequenceInputStream
import java.nio.charset.StandardCharsets
import scala.collection.JavaConverters._

class CentralFileHeader(fileName: String, fileSize: Long, offset: Long, timeStamp: DosDate, crcFunction: => Int)
    extends LittleEndianWriter with ZipFormat with ZipFormatSize {

  val versionMadeBy = unix | zipSpecification
  val extractorVersion = defaultVersion
  val flags = useUTF8
  val fileNameSize = fileNameBytes.size

  val stream: InputStream = new SequenceInputStream(Iterator(
      headerStream,
      fileNameStream).asJavaEnumeration)

  def size: Int = centralDirectoryHeader + fileNameSize
  
  private def headerStream = new LazyByteArrayInputStream(
    writeInt(centralFileHeaderSignature) ++
      writeShort(versionMadeBy) ++
      writeShort(extractorVersion) ++
      writeShort(flags) ++
      writeShort(noCompression) ++
      writeShort(timeStamp.time) ++
      writeShort(timeStamp.date) ++
      writeInt(crcFunction) ++
      writeInt(fileSize.toInt) ++
      writeInt(fileSize.toInt) ++
      writeShort(fileNameSize) ++
      writeShort(empty) ++
      writeShort(empty) ++
      writeShort(diskNumber) ++
      writeShort(empty) ++
      writeInt(readWriteFile) ++
      writeInt(offset.toInt))
  
  private def fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8)
  private def fileNameStream: InputStream = new ByteArrayInputStream(fileNameBytes)
}