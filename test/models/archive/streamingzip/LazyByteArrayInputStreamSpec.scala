package models.archive.streamingzip

import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class LazyByteArrayInputStreamSpec extends Specification {

  "LazyByteArrayInputStream" should {

    "not read bytes when created" in new LazyContext {
      bytesGenerated must beFalse
    }
    
    "generate bytes when calling read" in new LazyContext {
      stream.read must be equalTo data(0).toInt
      
      bytesGenerated must beTrue
    }

    "generate bytes when calling read(Array[Byte])" in new LazyContext {
       val bytes = new Array[Byte](numberOfBytes)
       
       stream.read(bytes) must be equalTo numberOfBytes
       
       bytes must be equalTo data
    }
    
    "generate bytes when calling read(Array[Byte], offset, len)" in new LazyContext {
      val bytesToRead = numberOfBytes / 2
      val bytes = new Array[Byte](bytesToRead)
      
      stream.read(bytes, 0, bytesToRead)
      
      bytes must be equalTo data.take(bytesToRead)
    }

    "implement available" in new LazyContext {
      stream.available must be equalTo numberOfBytes  
    }
    
    "close the stream" in new LazyContext {
      stream.close
      
      bytesGenerated must beTrue
    } 
    
    "implement markSupported" in new LazyContext {
      stream.markSupported must beTrue
    }
    
    "implement mark and reset" in new LazyContext {
      stream.mark(numberOfBytes)
      
      stream.read
      stream.reset()
      
      stream.read must be equalTo(data(0).toInt)
    }
    
    "implement skip" in new LazyContext {
      stream.skip(numberOfBytes + 1) must be equalTo numberOfBytes
    }

    trait LazyContext extends Scope {
      val stream = new LazyByteArrayInputStream(generateBytes)

      val numberOfBytes = 8
      var bytesGenerated = false
      val data = Array.tabulate[Byte](numberOfBytes)(_.toByte)

      def generateBytes = {
        bytesGenerated = true
        data
      }
    }
  }

}