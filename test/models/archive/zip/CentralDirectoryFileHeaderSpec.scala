package models.archive.zip

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import java.util.zip.CRC32
import java.io.InputStream
import java.util.Calendar
import java.util.zip.CheckedInputStream
import java.io.ByteArrayInputStream
import models.archive.DosDate
import models.archive.StreamReader


class CentralDirectoryFileHeaderSpec extends Specification {
  
  "CentralDirectoryFileHeader" should {
    
    "write header in stream" in new CentralDirectoryFileHeaderContext {
      val output = readStream(header.stream)

      val expectedHeader =
        writeInt(0x02014b50) ++
          writeShort(0x033F) ++
          writeShort(10) ++
          writeShort(0x0800) ++
          writeShort(0) ++
          writeShort(timeStamp.time.toShort) ++
          writeShort(timeStamp.date.toShort) ++
          writeInt(checksum) ++
          writeInt(fileSize) ++
          writeInt(fileSize) ++
          writeShort(fileName.length) ++
          writeShort(0) ++
          writeShort(0) ++
          writeShort(0) ++
          writeShort(0) ++
          writeInt(0x81800000) ++
          writeInt(offset)

      output.take(fixedHeaderSize) must be equalTo expectedHeader
    }
    
    "write fileName in stream" in new CentralDirectoryFileHeaderContext {
       val output = readStream(header.stream)
       
       output.drop(fixedHeaderSize) must be equalTo fileName.getBytes
    }
    
    trait CentralDirectoryFileHeaderContext extends Scope with StreamReader with LittleEndianWriter {
      val fixedHeaderSize = 46
      
      val offset = 0x12345678
      val fileSize = 10
      val checksum = 31415926
      val timeStamp = DosDate(Calendar.getInstance())
      val fileName = "file name"
      
      
      val header = new CentralDirectoryFileHeader(fileName, fileSize, checksum, offset, timeStamp)
      
    }
    
    
  }

}