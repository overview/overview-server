package models.archive.streamingzip

import org.specs2.mutable.Specification
import models.archive.ArchiveEntry
import org.specs2.mock.Mockito
import models.archive.CRCInputStream

class Zip64ArchiveStreamSpec extends Specification with Mockito {

  val EndOfArchiveSize = 56 + 20 + 22
  val LocalFileHeaderSize = 30
  val ExtraFieldSize = 32
  val DataDescriptorSize = 24
  val FileHeaderSize = 46
  
  "Zip64ArchiveStream" should {
    
    "return size of an empty archive" in {
      val archiveStream = new Zip64ArchiveStream(Iterable.empty)
      
      archiveStream.streamLength must be equalTo(EndOfArchiveSize)
    }
    
    "return size of an archive with files" in {
      val entrySize = 100
      val numberOfEntries = 10
      val fileNameSize = 5
      val archiveEntries = Seq.tabulate(numberOfEntries)(n =>
        ArchiveEntry(entrySize, s"name$n", mock[CRCInputStream]))
        
      val archiveStream = new Zip64ArchiveStream(archiveEntries)
      
      archiveStream.streamLength must be equalTo(numberOfEntries * (
          LocalFileHeaderSize + ExtraFieldSize + DataDescriptorSize 
           + fileNameSize + entrySize +
           FileHeaderSize + ExtraFieldSize + fileNameSize) + EndOfArchiveSize)
    }
  }
}