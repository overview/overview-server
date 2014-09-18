package models.archive.streamingzip

import org.specs2.mutable.Specification

class Zip64CentralFileHeaderSpec extends Specification {

  "Zip64CentralFileHeader" should {
    
    "report size including filename" in {
      val fileName = "file name"
      val centralFileHeader = new Zip64CentralFileHeader(fileName)
      
      centralFileHeader.size must be equalTo(fileName.size + 46 + 32)
    }
  }
}