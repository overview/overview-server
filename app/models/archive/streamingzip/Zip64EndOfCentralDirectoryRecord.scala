package models.archive.streamingzip

import java.io.ByteArrayInputStream
import java.io.InputStream


/** A Zip64 End Of Central Directory Record */
class Zip64EndOfCentralDirectoryRecord(localFileEntries: Seq[Zip64LocalFileEntry],
    centralDirectory: Seq[Zip64CentralFileHeader]) extends LittleEndianWriter {
  val signature = 0x06064b50
  val size: Int = 56
  val remainingSize: Int = size - 12 // Don't count the first 2 fields 
  val versionMadeBy: Short = 0x033F
  val extractorVersion: Short = 45
  val centralDirectorySize = centralDirectory.map(_.size).sum
  val centralDirectoryOffset = localFileEntries.map(_.size).sum
  
  private val numberOfEntries = localFileEntries.size


  def stream: InputStream = new ByteArrayInputStream(
    writeInt(signature) ++
      writeLong(remainingSize) ++
      writeShort(versionMadeBy) ++
      writeShort(extractorVersion) ++
      writeInt(0) ++
      writeInt(0) ++
      writeLong(numberOfEntries) ++
      writeLong(numberOfEntries) ++
      writeLong(centralDirectorySize) ++
      writeLong(centralDirectoryOffset))
}