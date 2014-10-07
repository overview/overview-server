package models.archive.streamingzip

import java.io.InputStream
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class Zip64EndOfCentralDirectoryRecordSpec extends Specification with Mockito {

  "Zip64EndOfCentralDirectoryRecord" should {

    "write static values in stream" in new EndOfCentralDirectoryRecordContext {

      val expectedFixedValues =
        writeInt(0x06064b50) ++
          writeLong(56 - 12) ++
          writeShort(0x033F) ++
          writeShort(0x002d) ++
          writeInt(0) ++
          writeInt(0)

      val output = readStream(endOfCentralDirectoryRecord.stream)
      
      output.take(staticFieldsLength) must be equalTo expectedFixedValues
    }

    "write dynamic fields in stream" in new EndOfCentralDirectoryRecordContext {
     val output = readStream(endOfCentralDirectoryRecord.stream)
      
     val expectedDynamicValues = 
       writeLong(numberOfEntries) ++
       writeLong(numberOfEntries) ++
       writeLong(centralDirectorySize) ++
       writeLong(localFileEntries.map(_.size).sum)
       
       
      output.drop(staticFieldsLength) must be equalTo expectedDynamicValues
      
    }
    
    trait EndOfCentralDirectoryRecordContext extends Scope with LittleEndianWriter {
      val numberOfEntries = 10
      val fileSize = 5E9.toLong
      val data = mock[InputStream]
      val staticFieldsLength = 24
      val centralFileHeaderSize = 100
      val centralDirectorySize = numberOfEntries * centralFileHeaderSize
      
      val localFileEntries = Seq.tabulate(numberOfEntries)(n =>
        new Zip64LocalFileEntry(f"file$n%2d", fileSize, data))
      val centralFileHeader = smartMock[Zip64CentralFileHeader]
      centralFileHeader.size returns centralFileHeaderSize
      
      val centralFileHeaders = Seq.fill(numberOfEntries)(centralFileHeader)

      val endOfCentralDirectoryRecord = new Zip64EndOfCentralDirectoryRecord(localFileEntries, centralFileHeaders)

      def readStream(s: InputStream): Array[Byte] = Stream.continually(s.read).takeWhile(_ != -1).toArray.map(_.toByte)
    }
  }
}