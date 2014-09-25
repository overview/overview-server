package models.archive.streamingzip

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import org.specs2.mock.Mockito
import models.archive.CRCInputStream
import java.io.InputStream

class Zip64EndOfCentralDirectoryRecordSpec extends Specification with Mockito {

  "Zip64EndOfCentralDirectoryRecord" should {

    "write static values in stream" in new EndOfCentralDirectoryRecordContext {

      val expectedFixedValues =
        writeInt(0x06064b50) ++
          writeInt(endOfCentralDirectoryRecord.size - 12) ++ writeInt(0) ++
          writeShort(0x033F) ++
          writeShort(0x000a) ++
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
       writeLong(localFileEntries.map(_.size).sum) ++
       writeLong(0)
       
      output.drop(staticFieldsLength) must be equalTo expectedDynamicValues
      
    }
    
    trait EndOfCentralDirectoryRecordContext extends Scope with LittleEndianWriter {
      val numberOfEntries = 10
      val fileSize = 5E9.toLong
      val data = mock[CRCInputStream]
      val staticFieldsLength = 24

      val localFileEntries = Seq.tabulate(numberOfEntries)(n =>
        Zip64LocalFileEntry(f"file$n%2d", fileSize, data))

      val endOfCentralDirectoryRecord = new Zip64EndOfCentralDirectoryRecord(localFileEntries)

      def readStream(s: InputStream): Array[Byte] = Stream.continually(s.read).takeWhile(_ != -1).toArray.map(_.toByte)
    }
  }
}