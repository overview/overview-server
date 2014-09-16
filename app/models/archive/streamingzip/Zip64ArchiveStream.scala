package models.archive.streamingzip

import models.archive.ArchiveEntry
import models.archive.ArchiveStream

class Zip64ArchiveStream(entries: Iterable[ArchiveEntry]) extends ArchiveStream(entries) {

  override def streamLength: Long =  
    Zip64EndOfCentralDirectoryRecord.size +
    Zip64EndOfCentralDirectoryLocator.size +
    EndOfCentralDirectoryRecord.size
    
  
  override def read(): Int = ???
}

