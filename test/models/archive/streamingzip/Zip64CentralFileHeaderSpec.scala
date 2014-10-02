package models.archive.streamingzip

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Calendar
import java.util.zip.CRC32
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import models.archive.streamingzip.HexByteString._
import java.util.zip.CheckedInputStream

class Zip64CentralFileHeaderSpec extends Specification {

  "Zip64CentralFileHeader" should {

    "report size including filename" in new CentralFileHeaderContext {
      centralFileHeader.size must be equalTo (fileName.size + 46 + extraFieldLength)
    }
    
    "report actual size" in new CentralFileHeaderContext {
      val output = readStream(centralFileHeader.stream)
      
      output.length must be equalTo centralFileHeader.size
    }

    "write header in stream" in new CentralFileHeaderContext {
      val crc = new CRC32
      crc.update(data)
      readStream(fileStream)

      val output = readStream(centralFileHeader.stream)

      val expectedHeader =
        writeInt(0x02014b50) ++
          writeShort(0x033F) ++
          writeShort(0x002d) ++
          writeShort(0x0800) ++
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

    "write filename in stream" in new CentralFileHeaderContext {
      val output = readStream(centralFileHeader.stream)

      output.drop(fixedHeaderSize).take(fileName.length) must be equalTo fileName.getBytes
    }

    "write extra field in stream" in new CentralFileHeaderContext {
      val output = readStream(centralFileHeader.stream)

      val expectedExtraField = 
        writeShort(0x01) ++
        writeShort(extraFieldDataLength.toShort) ++
        writeLong(fileSize) ++ 
        writeLong(fileSize) ++
        writeLong(offset) 
        
        
      output.drop(fixedHeaderSize + fileName.length) must be equalTo expectedExtraField
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

      val centralFileHeader = new Zip64CentralFileHeader(fileName, fileSize, offset, timeStamp, fileStream.getChecksum.getValue.toInt)

      def readStream(s: InputStream): Array[Byte] = Stream.continually(s.read).takeWhile(_ != -1).toArray.map(_.toByte)
    }
  }
}