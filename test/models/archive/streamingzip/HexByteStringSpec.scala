package models.archive.streamingzip

import org.specs2.mutable.Specification

class HexByteStringSpec extends Specification {

  import HexByteString._
  
  "HexByteString" should {
    
    "create an empty byte array for an empty string" in {
      "".hex must be equalTo Array.empty[Byte]
    }
    
    "convert arguments to bytes" in {
      "fffe01".hex must be equalTo Array(0xff, 0xfe, 0x01).map(_.toByte)
    }
    
    "ignore invalid bytes" in {
      "feedme".hex must be equalTo Array(0xfe, 0xed).map(_.toByte)
    }
  }
}