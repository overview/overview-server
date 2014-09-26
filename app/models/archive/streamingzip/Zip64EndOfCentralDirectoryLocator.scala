package models.archive.streamingzip

import java.io.InputStream
import java.io.ByteArrayInputStream

class Zip64EndOfCentralDirectoryLocator(centralFileHeaders: Iterable[Zip64CentralFileHeader]) extends LittleEndianWriter {
  val size: Int = 20
  val signature = 0x07064b50
  val diskNumber = 0
  val totalNumberOfDisks = 1
  private val endOfCentralDirectoryOffset = centralFileHeaders.map(_.size).sum
  
  val stream: InputStream = new ByteArrayInputStream(
    writeInt(signature) ++
    writeInt(diskNumber) ++
    writeLong(endOfCentralDirectoryOffset) ++
    writeInt(totalNumberOfDisks)
  )
}

