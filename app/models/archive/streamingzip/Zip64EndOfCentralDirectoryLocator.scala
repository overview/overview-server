package models.archive.streamingzip

import java.io.ByteArrayInputStream
import java.io.InputStream

/** Zip64 End of Central Directory Locator */
class Zip64EndOfCentralDirectoryLocator(localFileEntries: Iterable[Zip64LocalFileEntry], 
    centralFileHeaders: Iterable[Zip64CentralFileHeader]) extends LittleEndianWriter {
  val size: Int = 20
  val signature = 0x07064b50
  val diskNumber = 0
  val totalNumberOfDisks = 1
  private val endOfCentralDirectoryOffset = localFileEntries.map(_.size).sum + centralFileHeaders.map(_.size).sum
  
  val stream: InputStream = new ByteArrayInputStream(
    writeInt(signature) ++
    writeInt(diskNumber) ++
    writeLong(endOfCentralDirectoryOffset) ++
    writeInt(totalNumberOfDisks)
  )
}

