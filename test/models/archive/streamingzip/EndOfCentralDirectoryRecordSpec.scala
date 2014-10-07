package models.archive.streamingzip

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import java.io.InputStream
import org.specs2.mock.Mockito

class EndOfCentralDirectoryRecordSpec extends Specification with Mockito {

  "EndOfCentralDirectoryRecord" should {

    "write values to stream in" in new CentralDirectoryContext {

      val expectedValues =
        writeInt(0x06054b50) ++
          writeShort(0) ++
          writeShort(0) ++
          writeShort(numberOfEntries) ++
          writeShort(numberOfEntries) ++
          writeInt(centralDirectorySize) ++
          writeInt(centralDirectoryOffset) ++
          writeShort(0)

      val output = readStream(endOfCentralDirectoryRecord.stream)

      output must be equalTo expectedValues

    }

    trait CentralDirectoryContext extends Scope with LittleEndianWriter {
      val numberOfEntries = 10
      val fileSize = 1024
      val data = smartMock[InputStream]
      val centralFileHeaderSize = 100

      val localFileEntries = Seq.tabulate(numberOfEntries)(n =>
        new LocalFileEntry(f"file$n%2d", fileSize, data))
      val centralFileHeader = smartMock[CentralFileHeader]
      centralFileHeader.size returns centralFileHeaderSize

      val centralFileHeaders = Seq.fill(numberOfEntries)(centralFileHeader)

      val endOfCentralDirectoryRecord = new EndOfCentralDirectoryRecord(localFileEntries, centralFileHeaders)

      val centralDirectorySize = numberOfEntries * centralFileHeaderSize
      val centralDirectoryOffset = localFileEntries.map(_.size).sum.toInt
      
      def readStream(stream: InputStream): Array[Byte] =
        Stream.continually(stream.read).takeWhile(_ != -1).map(_.toByte).toArray
    }
  }

}