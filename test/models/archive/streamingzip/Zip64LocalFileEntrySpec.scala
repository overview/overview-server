package models.archive.streamingzip

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder._
import java.nio.charset.StandardCharsets
import java.util.Calendar
import org.specs2.mock.Mockito
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import models.archive.streamingzip.HexByteString._
import java.util.zip.CRC32

class Zip64LocalFileEntrySpec extends Specification with Mockito {

  "Zip64LocalFileEntry" should {

    "return size" in new FileEntryContext {
      entry.size must be equalTo (entrySize(fileName.size))
    }
    
    "return actual size" in new FileEntryContext {
      val output = readStream(entry.stream)
      
      output.length.toLong must be equalTo(entry.size)
    }

    "count UTF-8 filename size correctly" in new FileEntryContext {
      val utf8Bytes = "d0add09cd098d09bd098".hex

      val utf8FileName = new String(utf8Bytes, StandardCharsets.UTF_8)

      val utf8Entry = new Zip64LocalFileEntry(utf8FileName, fileSize, fileStream)

      utf8Entry.size must be equalTo (entrySize(utf8FileName.size * 2))

    }


    "write header in stream" in new FileEntryContext {
      val output = new Array[Byte](localFileHeaderSize)

      val n = entry.stream.read(output)
      val expectedHeader =
        writeInt(0x04034b50) ++ // signature
        writeShort(0x002d) ++   // version
        writeShort(0x0808) ++   // flag
        writeShort(0) ++        // compression
        writeShort(0) ++        // time
        writeShort(0) ++        // date
        writeInt(0)  ++         // crc32
        writeInt(-1) ++ // compressed size
        writeInt(-1) ++ // uncompressed size
        writeShort(0x000a) ++   // filename length
        writeShort(20)           // extra field length

      // don't check date and time fields
      output.take(10) must be equalTo (expectedHeader.take(10))
      output.drop(14) must be equalTo (expectedHeader.drop(14))
    }

    "write date and time in stream" in new FileEntryContext {
      val output = new Array[Byte](localFileHeaderSize)

      val now = Calendar.getInstance()

      val n = entry.stream.read(output)
      val timeBytes = output.slice(10, 12)
      val dateBytes = output.slice(12, 14)

      val dosTime = bytesToInt(timeBytes)
      val dosDate = bytesToInt(dateBytes)

      val fileTime = DosDate.toCalendar(dosDate, dosTime)

      now.getTimeInMillis must be closeTo (fileTime.getTimeInMillis, 5000)

    }

    "write file name" in new FileEntryContext {
      val output = readStream(entry.stream)

      output.slice(localFileHeaderSize, localFileHeaderSize + fileName.size) must be equalTo fileName.getBytes
    }

    "write extra field" in new FileEntryContext {
      val output = readStream(entry.stream) 
      
      val expectedExtraField = 
        writeShort(1) ++
        writeShort(16) ++
        writeLong(0) ++
        writeLong(0)
        
      output.slice(localFileHeaderSize + fileName.size, localFileHeaderSize + fileName.size + extraFieldSize) must be equalTo expectedExtraField
    }
    
    "write file" in new FileEntryContext {

      val output = readStream(entry.stream)

      output.slice(localFileHeaderSize + fileName.size + extraFieldSize, 
          localFileHeaderSize + fileName.size + extraFieldSize + fileSize) must be equalTo fileData
    }

    "write data descriptor" in new FileEntryContext {
      val output = readStream(entry.stream)

      val expectedDataDescriptor =
        writeInt(0x08074b50)  ++       // signature
        writeInt(crc32.toInt) ++       // crc32
        writeLong(fileSize)  ++        // original size
        writeLong(fileSize)            // compressed size 	
          

      output.drop(localFileHeaderSize + fileName.size + extraFieldSize + fileSize) must be equalTo expectedDataDescriptor
    }

    trait FileEntryContext extends Scope with LittleEndianWriter {
      val fileName = "1234567890"

      val localFileHeaderSize = 30
      val dataDescriptorSize = 24
      val extraFieldSize = 20
      
      val fileSize = 100
      val fileData = Array.tabulate(fileSize)(_.toByte)
      val fileStream = new ByteArrayInputStream(fileData)
      val crc32 = {
        val checker = new CRC32()
        
        checker.update(fileData)
        checker.getValue
      }

      val entry = new Zip64LocalFileEntry(fileName, fileSize, fileStream)

      def entrySize(fileNameSize: Int) =
        localFileHeaderSize + dataDescriptorSize + extraFieldSize + fileSize + fileNameSize

      // assumes 2 bytes in array
      def bytesToInt(bytes: Array[Byte]): Int = {
        val byteBuffer = ByteBuffer.allocate(4).order(LITTLE_ENDIAN)

        byteBuffer.put(bytes)

        byteBuffer.getShort(0)
      }
      
      def readStream(stream: InputStream): Array[Byte] =
        Stream.continually(stream.read).takeWhile(_ != -1).map(_.toByte).toArray
    }
  }

}