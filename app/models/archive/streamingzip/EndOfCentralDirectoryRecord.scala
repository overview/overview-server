package models.archive.streamingzip

import java.io.InputStream
import java.io.ByteArrayInputStream


/** 
 *  End of Central Directory Record for a Zip64 formatted archive.
 *  Most values are actually found in the Zip64 End of Central Directory Record
 */
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