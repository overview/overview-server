package models.archive.streamingzip

import java.io.ByteArrayInputStream
import java.io.InputStream

/** Zip64 End of Central Directory Locator */
class Zip64EndOfCentralDirectoryLocator(localFileEntries: Iterable[Zip64LocalFileEntry],
                                        centralFileHeaders: Iterable[Zip64CentralFileHeader]) extends LittleEndianWriter with ZipFormat {

  private val endOfCentralDirectoryOffset = localFileEntries.map(_.size).sum + centralFileHeaders.map(_.size).sum

  val stream: InputStream = new ByteArrayInputStream(
    writeInt(zip64EndOfCentralDirectoryLocatorSignature) ++
      writeInt(diskNumber) ++
      writeLong(endOfCentralDirectoryOffset) ++
      writeInt(totalNumberOfDisks))
}

