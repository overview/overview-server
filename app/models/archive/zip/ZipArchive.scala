package models.archive.zip

import models.archive.Archive
import java.io.InputStream
import models.archive.ArchiveEntry
import models.archive.ComposedInputStream

class ZipArchive(entries: Seq[ArchiveEntry]) extends Archive(entries)  {

  override def stream: InputStream = new ComposedInputStream(
      localFileCollection.stream _,
      centralDirectory.stream _,
      endOfCentralDirectoryRecord.stream _)
  
  
  override def size: Long = localFileCollection.size + centralDirectory.size + endOfCentralDirectoryRecord.size
  
  private val localFileCollection = new LocalFileCollection(entries)
  private val centralDirectory = new CentralDirectory(localFileCollection.entries)
  private val endOfCentralDirectoryRecord = 
    new EndOfCentralDirectoryRecord(entries.size, centralDirectory.size, centralDirectory.offset) 
  
}