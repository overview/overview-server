package models.archive.streamingzip

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.SequenceInputStream
import java.nio.charset.StandardCharsets
import scala.collection.JavaConverters._
import models.archive.CRCInputStream

/**
 * Central Directory File Headers in Zip64 format
 */
class Zip64CentralFileHeader(fileName: String, fileSize: Long, offset: Long, timeStamp: DosDate, data: CRCInputStream) extends LittleEndianWriter {
  private val DataSize = 46
  private val ExtraFieldSize = 28

  private val fileNameSize = fileNameBytes.length

  val signature: Int = 0x02014b50
  val versionMadeBy: Short = 0x033F
  val flags: Short = 0x0800
  val extractorVersion: Short = 45  // Version 4.5 Uses Zip64 extension
  val compression: Short = 0
  val unused: Int = -1
  val empty: Short = 0
  val extraFieldDataSize = ExtraFieldSize - 4
  
  val zip64Tag: Short = 1

  def size: Int = DataSize + ExtraFieldSize + fileNameSize

  def stream: InputStream = new SequenceInputStream(Iterator(
    headerStream,
    fileNameStream,
    extraFieldStream).asJavaEnumeration)

  private def headerStream = new LazyByteArrayInputStream(
    writeInt(signature) ++
      writeShort(versionMadeBy) ++
      writeShort(extractorVersion) ++
      writeShort(flags) ++
      writeShort(compression) ++
      writeShort(timeStamp.time.toShort) ++
      writeShort(timeStamp.date.toShort) ++
      writeInt(data.crc32.toInt) ++
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
    writeShort(zip64Tag) ++
      writeShort(extraFieldDataSize.toShort) ++
      writeLong(fileSize) ++
      writeLong(fileSize) ++
      writeLong(offset) 
      )

}