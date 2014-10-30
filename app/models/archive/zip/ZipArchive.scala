package models.archive.zip

import models.archive.Archive
import java.io.InputStream
import models.archive.ArchiveEntry
import models.archive.ComposedInputStream
import models.archive.ArchiveEntryCollection

class ZipArchive(entryCollection: ArchiveEntryCollection) extends Archive  {

  override def stream: InputStream = new ComposedInputStream(
      localFileCollection.stream _,
      centralDirectory.stream _,
      endOfCentralDirectoryRecord.stream _)
  
  
  override def size: Long = localFileCollection.size + centralDirectory.size + endOfCentralDirectoryRecord.size
  
  private val entries = entryCollection.sanitizedEntries
  
  private val localFileCollection = new LocalFileCollection(entries)
  private val centralDirectory = new CentralDirectory(localFileCollection.entries)
  private val endOfCentralDirectoryRecord = 
    new EndOfCentralDirectoryRecord(entries.size, centralDirectory.size, centralDirectory.offset) 
  
}