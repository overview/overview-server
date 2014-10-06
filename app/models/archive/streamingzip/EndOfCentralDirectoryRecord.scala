package models.archive.streamingzip

import java.io.InputStream
import java.io.ByteArrayInputStream

class EndOfCentralDirectoryRecord(localFileEntries: Seq[LocalFileEntry],
                                  centralDirectory: Seq[CentralFileHeader]) extends LittleEndianWriter with ZipFormat {

  val centralDirectorySize = centralDirectory.map(_.size).sum.toInt
  val centralDirectoryOffset = localFileEntries.map(_.size).sum.toInt

  private val numberOfEntries = localFileEntries.size

  val stream: InputStream = new ByteArrayInputStream(
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