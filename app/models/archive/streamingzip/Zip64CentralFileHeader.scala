package models.archive.streamingzip

import java.nio.charset.StandardCharsets
import java.io.InputStream
import java.io.ByteArrayInputStream
import models.archive.CRCInputStream

class Zip64CentralFileHeader(fileName: String, timeStamp: DosDate, data: CRCInputStream) extends LittleEndianWriter {
  private val DataSize = 46
  private val ExtraFieldSize = 32
  private val fileNameSize = fileName.getBytes(StandardCharsets.UTF_8).length
  
  val signature: Int = 0x02014b50
  val versionMadeBy: Short = 0x033F
  val flags: Short = 0x0808
  val extractorVersion: Short = 10
  val compression: Short = 0
  val unused: Int = -1
  val empty: Short = 0

  
  def size: Int = DataSize + ExtraFieldSize + fileNameSize
  
  def stream: InputStream = headerStream
  
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
    writeInt(unused)
  )
    

}