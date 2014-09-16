package models.archive.streamingzip

import org.specs2.mutable.Specification

class Zip64ArchiveStreamSpec extends Specification {

  "Zip64ArchiveStream" should {
    
    "return size of an empty archive" in {
      val archiveStream = new Zip64ArchiveStream(Iterable.empty)
      
      archiveStream.streamLength must be equalTo(56 + 20 + 22)
    }
  }
}