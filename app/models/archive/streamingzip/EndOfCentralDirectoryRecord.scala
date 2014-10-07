package models.archive.streamingzip

import java.io.InputStream
import java.io.ByteArrayInputStream


/**
 * Zip End of Central Directory Record
 */
class EndOfCentralDirectoryRecord(localFileEntries: Seq[LocalFileEntry],
                                  centralDirectory: Seq[CentralFileHeader]) extends LittleEndianWriter with ZipFormat {

  protected val centralDirectorySize = centralDirectory.map(_.size).sum.toInt
  protected val centralDirectoryOffset = localFileEntries.map(_.size).sum.toInt

  protected val numberOfEntries = localFileEntries.size

  lazy val stream: InputStream = new ByteArrayInputStream(
    writeInt(endOfCentralDirectoryRecordSignature) ++
      writeShort(diskNumber) ++
      writeShort(diskNumber) ++ 
      writeShort(numberOfEntries) ++
      writeShort(numberOfEntries) ++
      writeInt(centralDirectorySize) ++
      writeInt(centralDirectoryOffset) ++
      writeShort(empty)
  )

}