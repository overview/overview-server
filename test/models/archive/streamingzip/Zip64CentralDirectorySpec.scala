package models.archive.streamingzip

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import models.archive.CRCInputStream


class Zip64CentralDirectorySpec extends Specification with Mockito {

  
  
  "Zip64CentralDirectory" should {
    
    "return size for an empty archive" in {
      val zip64EndOfCentralDirectoryRecordSize = 56
      val zip64EndOfCentralDirectoryLocatorSize = 20
      val endOfCentralDirectoryRecordSize = 22

      val centralDirectory = new Zip64CentralDirectory(Seq.empty)
      
      centralDirectory.size must be equalTo(zip64EndOfCentralDirectoryRecordSize + zip64EndOfCentralDirectoryLocatorSize +
          endOfCentralDirectoryRecordSize)
    }
    
    "return size for an archive with files" in {
      val numberOfEntries = 10
      val fileSize = 100
      val localFileEntries = Seq.tabulate(10)(n => new Zip64LocalFileEntry(s"name$n", fileSize, mock[CRCInputStream]))
      val directoryHeaderSize = 46 + 5 + 32 
        
      val zip64EndOfCentralDirectoryRecordSize = 56
      val zip64EndOfCentralDirectoryLocatorSize = 20
      val endOfCentralDirectoryRecordSize = 22

      val centralDirectory = new Zip64CentralDirectory(localFileEntries)
      
      centralDirectory.size must be equalTo(10 * directoryHeaderSize + zip64EndOfCentralDirectoryRecordSize + zip64EndOfCentralDirectoryLocatorSize +
          endOfCentralDirectoryRecordSize)
      
    }
  }
}