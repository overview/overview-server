package models.archive.streamingzip

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import java.io.ByteArrayInputStream
import scala.util.Random

class StoredInputStreamSpec extends Specification {

  "StoredInputStream" should {
    
    "return 0 crc32 value before stream is read" in new StreamContext {
      inputStream.crc32 must be equalTo(0)
    }
    
    trait StreamContext extends Scope {
      val inputData = Array.ofDim[Byte](128)
      Random.nextBytes(inputData)
      
      val input = new ByteArrayInputStream(inputData)
      val inputStream = new StoredInputStream(input)
    }
  }
}