package models.archive.zip

import java.io.InputStream
import java.io.ByteArrayInputStream

/**
 * Zip End of Central Directory Record
 */
class EndOfCentralDirectoryRecord(numberOfEntries: Long, centralDirectorySize: Long, centralDirectoryOffset: Long)
    extends LittleEndianWriter with ZipFormat with ZipFormatSize {

  def stream: InputStream = new ByteArrayInputStream(
    writeInt(endOfCentralDirectoryRecordSignature) ++
      writeShort(diskNumber) ++
      writeShort(diskNumber) ++
      writeShort(numberOfEntries.toInt) ++
      writeShort(numberOfEntries.toInt) ++
      writeInt(centralDirectorySize.toInt) ++
      writeInt(centralDirectoryOffset.toInt) ++
      writeShort(empty))

  def size: Long = endOfCentralDirectory
}