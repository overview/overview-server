package models.archive.streamingzip

import java.io.ByteArrayInputStream
import java.io.InputStream


/** A Zip64 End Of Central Directory Record */
class Zip64EndOfCentralDirectoryRecord(localFileEntries: Seq[Zip64LocalFileEntry],
    centralDirectory: Seq[Zip64CentralFileHeader]) extends LittleEndianWriter with ZipFormat {

  val size = 56
  val remainingSize = size - 12 // Don't count the first 2 fields 
  val versionMadeBy = unix | zipSpecification
  
  val centralDirectorySize = centralDirectory.map(_.size).sum
  val centralDirectoryOffset = localFileEntries.map(_.size).sum
  
  private val numberOfEntries = localFileEntries.size


  def stream: InputStream = new ByteArrayInputStream(
    writeInt(zip64EndOfCentralDirectorySignature) ++
      writeLong(remainingSize) ++
      writeShort(versionMadeBy) ++
      writeShort(useZip64Format) ++
      writeInt(empty) ++
      writeInt(empty) ++
      writeLong(numberOfEntries) ++
      writeLong(numberOfEntries) ++
      writeLong(centralDirectorySize) ++
      writeLong(centralDirectoryOffset))
}