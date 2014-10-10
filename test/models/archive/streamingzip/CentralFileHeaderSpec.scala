package models.archive.streamingzip

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import java.util.zip.CRC32
import java.io.InputStream
import java.util.Calendar
import java.util.zip.CheckedInputStream
import java.io.ByteArrayInputStream
import models.archive.DosDate


class CentralFileHeaderSpec extends Specification {
  
  "CentralFileHeader" should {
    
    "write header in stream" in new CentralFileHeaderContext {
      val crc = new CRC32
      crc.update(data)
      readStream(fileStream)

      val output = readStream(centralFileHeader.stream)

      val expectedHeader =
        writeInt(0x02014b50) ++
          writeShort(0x033F) ++
          writeShort(10) ++
          writeShort(0x0800) ++
          writeShort(0) ++
          writeShort(timeStamp.time.toShort) ++
          writeShort(timeStamp.date.toShort) ++
          writeInt(crc.getValue.toInt) ++
          writeInt(fileSize) ++
          writeInt(fileSize) ++
          writeShort(fileName.length.toShort) ++
          writeShort(0) ++
          writeShort(0) ++
          writeShort(0) ++
          writeShort(0) ++
          writeInt(0x81800000) ++
          writeInt(offset)

      output.take(fixedHeaderSize) must be equalTo expectedHeader
    }
    
    "write fileName in stream" in new CentralFileHeaderContext {
       val output = readStream(centralFileHeader.stream)
       
       output.drop(fixedHeaderSize) must be equalTo fileName.getBytes
    }
    
    trait CentralFileHeaderContext extends Scope with LittleEndianWriter {
      val fixedHeaderSize = 46
      val extraFieldLength = 28
      val extraFieldDataLength = extraFieldLength - 4 
      
      val offset = 0x12345678
      val fileSize = 10
      val timeStamp = DosDate(Calendar.getInstance())
      val fileName = "file name"
      val data = Array.fill[Byte](fileSize)(0xba.toByte)
      val fileStream = new CheckedInputStream(new ByteArrayInputStream(data), new CRC32)

      val centralFileHeader = new CentralFileHeader(fileName, fileSize, offset, timeStamp, fileStream.getChecksum.getValue.toInt)

      def readStream(s: InputStream): Array[Byte] = Stream.continually(s.read).takeWhile(_ != -1).toArray.map(_.toByte)
    }
    
    
  }

}