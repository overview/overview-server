package models.archive.streamingzip

import java.io.InputStream
import java.io.ByteArrayInputStream

class EndOfCentralDirectoryRecord extends LittleEndianWriter {
  val size: Int = 22
  val signature = 0x06054b50
  val diskNumber: Short = 0
  val unused: Short = -1
  
  val stream: InputStream = new ByteArrayInputStream(
    writeInt(signature) ++
    writeShort(diskNumber) ++
    writeShort(diskNumber) ++
    writeShort(unused) ++
    writeShort(unused) ++
    writeInt(unused) ++
    writeInt(unused) ++
    writeShort(0)
  )
}