package models.archive.streamingzip

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import java.io.ByteArrayInputStream
import scala.util.Random
import java.util.zip.CRC32

class StoredInputStreamSpec extends Specification {

  "StoredInputStream" should {
    
    "return 0 crc32 value before stream is read" in new StreamContext {
      inputStream.crc32 must be equalTo(0)
    }
    
    "calculate crc32 value when read() is called" in new StreamContext {
      val readBytes = Iterator.continually(inputStream.read())
        .takeWhile(_ != -1)
        .map(_.toByte).toArray
      
      readBytes must be equalTo(inputData)
      
      inputStream.crc32 must be equalTo(inputCrc32)
    }
    
    "calculate crc32 value when read(byte[]) is called" in new StreamContext {
      val readBytes = Array.ofDim[Byte](dataLength)
      
      inputStream.read(readBytes)
      
      readBytes must be equalTo(inputData)
      
      inputStream.crc32 must be equalTo(inputCrc32)
    }
    
    
    trait StreamContext extends Scope {
      val dataLength = 128
      val inputData = Array.ofDim[Byte](dataLength)
      Random.nextBytes(inputData)
      
      val input = new ByteArrayInputStream(inputData)
      val inputStream = new StoredInputStream(input)
      
      val inputCrc32 = {
        val crc32 = new CRC32
        
        crc32.update(inputData)
        
        crc32.getValue
      }
    }
  }
}