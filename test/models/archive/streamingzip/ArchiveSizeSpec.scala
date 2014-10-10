package models.archive.streamingzip

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import models.archive.ArchiveEntry
import org.specs2.mock.Mockito
import java.io.InputStream

class ArchiveSizeSpec extends Specification with Mockito {

  "ArchiveSize" should {

    "return the size of an empty archive" in new ArchiveContext {
      val entries = Seq.empty[ArchiveEntry]

      ArchiveSize(entries) must be equalTo endOfCentralDirectorySize
    }

    "return the size of a archive with files" in new Zip64ArchiveContext {
      val numberOfEntries = 7
      val fileSize = 100
      val fileNameSize = 13
      val entries = Seq.tabulate(numberOfEntries)(n => ArchiveEntry(fileSize, "n" * fileNameSize, () => smartMock[InputStream]))

      ArchiveSize(entries) must be equalTo
        numberOfEntries * (localFileHeaderSize + fileNameSize + fileSize + dataDescriptorSize) +
        numberOfEntries * (centralDirectoryHeaderSize + fileNameSize) +
        endOfCentralDirectorySize
    }

    trait ArchiveContext extends Scope {
      val endOfCentralDirectorySize = 22
    }
    
    trait Zip64ArchiveContext extends Scope {
      val zip64EndOfCentralDirectorySize = 56
      val zip64EndOfCentralDirectoryLocatorSize = 20
      val endOfCentralDirectorySize = 22

      val localFileHeaderSize = 30
      val localFileHeaderExtraFieldSize = 20
      val dataDescriptorSize = 24
      val centralDirectoryHeaderSize = 46
      val centralDirectoryExtraFieldSize = 28

      val endOfArchiveSize = zip64EndOfCentralDirectorySize +
        zip64EndOfCentralDirectoryLocatorSize +
        endOfCentralDirectorySize

    }
  }

}