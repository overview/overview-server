package models.archive.streamingzip

import java.io.ByteArrayInputStream
import java.io.InputStream

class Zip64EndOfCentralDirectoryRecord(entries: Seq[Zip64LocalFileEntry]) extends LittleEndianWriter {
  val signature = 0x06064b50
  val size: Int = 56
  val remainingSize: Int = size - 12 // Don't count the first 2 fields 
  val versionMadeBy: Short = 0x033F
  val extractorVersion: Short = 10
  val centralDirectoryOffset = entries.map(_.size).sum
  
  private val numberOfEntries = entries.size


  def stream: InputStream = new ByteArrayInputStream(
    writeInt(signature) ++
      writeLong(remainingSize) ++
      writeShort(versionMadeBy) ++
      writeShort(extractorVersion) ++
      writeInt(0) ++
      writeInt(0) ++
      writeLong(numberOfEntries) ++
      writeLong(numberOfEntries) ++
      writeLong(centralDirectoryOffset) ++
      writeLong(0))
}