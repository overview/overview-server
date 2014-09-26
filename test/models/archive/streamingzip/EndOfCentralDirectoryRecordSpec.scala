package models.archive.streamingzip

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import java.io.InputStream

class EndOfCentralDirectoryRecordSpec extends Specification {

  "EndOfCentralDirectoryRecord" should {
    
    "write values to stream in" in new CentralDirectoryContext {
      
      val expectedValues = 
        writeInt(0x06054b50) ++
        writeShort(0) ++
        writeShort(0) ++
        writeShort(-1) ++
        writeShort(-1) ++
        writeInt(-1) ++ 
        writeInt(-1) ++
        writeShort(0)
        
    val output = readStream(endOfCentralDirectoryRecord.stream)
    
    output must be equalTo expectedValues
      
    }
    
    trait CentralDirectoryContext extends Scope with LittleEndianWriter {
      val endOfCentralDirectoryRecord = new EndOfCentralDirectoryRecord
      
      def readStream(stream: InputStream): Array[Byte] =
        Stream.continually(stream.read).takeWhile(_ != -1).map(_.toByte).toArray
    }
  }
}