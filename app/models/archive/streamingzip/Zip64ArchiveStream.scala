package models.archive.streamingzip

import models.archive.ArchiveEntry
import models.archive.ArchiveStream

class Zip64ArchiveStream(entries: Iterable[ArchiveEntry]) extends ArchiveStream(entries) {

  private val localFileHeaders = createLocalFileHeaders(entries)
  private val centralDirectory = createCentralDirectory(localFileHeaders)
  
  override def streamLength: Long =  
    localFileHeaders.map(_.size).sum +
    centralDirectory.size
    
  
  override def read(): Int = ???
  
  
  private def createLocalFileHeaders(entries: Iterable[ArchiveEntry]): Seq[Zip64LocalFileEntry] =
    entries.map(Zip64LocalFileEntry(_, 0)).toSeq
    
    private def createCentralDirectory(fileHeaders: Iterable[Zip64LocalFileEntry]) = 
      new Zip64CentralDirectory(fileHeaders)
}



