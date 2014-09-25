package models.archive.streamingzip

import org.specs2.mutable.Specification
import models.archive.streamingzip.HexByteString._
import java.io.ByteArrayInputStream
import java.util.Calendar
import org.specs2.specification.Scope
import java.util.zip.CRC32

class Zip64CentralFileHeaderSpec extends Specification {

  "Zip64CentralFileHeader" should {

    "report size including filename" in new CentralFileHeaderContext {
      centralFileHeader.size must be equalTo (fileName.size + 46 + 32)
    }

    "write header in stream" in new CentralFileHeaderContext {
      val crc = new CRC32
      crc.update(data)
      readStream

      val output = new Array[Byte](fixedHeaderSize)

      centralFileHeader.stream.read(output)

      val expectedHeader =
        writeInt(0x02014b50) ++
          writeShort(0x033F) ++
          writeShort(0x000a) ++
          writeShort(0x0808) ++
          writeShort(0) ++
          writeShort(timeStamp.time.toShort) ++
          writeShort(timeStamp.date.toShort) ++
          writeInt(crc.getValue.toInt) ++
          writeInt(0xFFFFFFFF) ++
          writeInt(0xFFFFFFFF) ++
          writeShort(fileName.length.toShort) ++
          writeShort(extraFieldLength.toShort) ++
          writeShort(0) ++
          writeShort(0) ++
          writeShort(0) ++
          writeInt(0) ++
          writeInt(0xFFFFFFFF)

      output.take(fixedHeaderSize) must be equalTo expectedHeader
    }

    trait CentralFileHeaderContext extends Scope with LittleEndianWriter {
      val fixedHeaderSize = 46
      val extraFieldLength = 32

      val timeStamp = DosDate(Calendar.getInstance())
      val fileName = "file name"
      val data = Array.fill[Byte](10)(0xba.toByte)
      val fileStream = new StoredInputStream(new ByteArrayInputStream(data))

      val centralFileHeader = new Zip64CentralFileHeader(fileName, timeStamp, fileStream)

      def readStream: Array[Byte] = Stream.continually(fileStream.read).takeWhile(_ != -1).toArray.map(_.toByte)
    }
  }
}