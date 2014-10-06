package models.archive.streamingzip

import java.io.InputStream
import java.io.ByteArrayInputStream


/** 
 *  End of Central Directory Record for a Zip64 formatted archive.
 *  Most values are actually found in the Zip64 End of Central Directory Record,
 *  so values here are unused.
 */
class UnusedEndOfCentralDirectoryRecord extends LittleEndianWriter with ZipFormat {

  val stream: InputStream = new ByteArrayInputStream(
    writeInt(endOfCentralDirectoryRecordSignature) ++
    writeShort(diskNumber) ++
    writeShort(diskNumber) ++
    writeShort(unused) ++
    writeShort(unused) ++
    writeInt(unused) ++
    writeInt(unused) ++
    writeShort(empty)
  )
}