package models.archive.streamingzip

import org.specs2.mutable.Specification
import org.specs2.mock.Mockito
import models.archive.CRCInputStream
import java.nio.charset.StandardCharsets
import org.specs2.specification.Scope
import models.archive.streamingzip.HexByteString._
import java.util.Date
import java.util.Calendar
import java.nio.ByteBuffer
import java.nio.ByteOrder._
import java.io.ByteArrayInputStream
import java.io.InputStream

class Zip64LocalFileEntrySpec extends Specification with Mockito {

  "Zip64LocalFileEntry" should {

    "return size" in new FileEntryContext {
      val entry = new Zip64LocalFileEntry(fileName, fileSize, 0, data)

      entry.size must be equalTo (entrySize(fileName.size))
    }

    "count UTF-8 filename size correctly" in new FileEntryContext {
      val utf8Bytes = "d0add09cd098d09bd098".hex

      val utf8FileName = new String(utf8Bytes, StandardCharsets.UTF_8)
      
      val entry = new Zip64LocalFileEntry(utf8FileName, fileSize, 0, data)

      entry.size must be equalTo (entrySize(utf8FileName.size * 2))

    }
   
    "set values" in new FileEntryContext {
      val entry = new Zip64LocalFileEntry(fileName, fileSize, 0, data)

      entry.signature must be equalTo(0x04034b50)
      entry.extractorVersion must be equalTo(10)
      entry.flags must be equalTo(0x0808)
      entry.compression must be equalTo(0)
      entry.fileNameLength must be equalTo(fileName.length.toShort)
    }
    
    "write header in stream" in new FileEntryContext {
      val entry = new Zip64LocalFileEntry(fileName, fileSize, 0, data)

      val output = new Array[Byte](localFileHeaderSize)
      
      val n = entry.stream.read(output)
      val expectedHeader = 
        "504b0304" +     // signature
        "0a00" +         // version
        "0808" +         // flag
        "0000" +         // compression
        "0000" +         // time
        "0000" +         // date
        "00000000" +     // crc32
        "00000000" +     // compressed size
        "00000000" +     // uncompressed size
        "0a00" +         // filename length
        "2000"           // extra field length
       
        // don't check date and time fields
      output.take(10) must be equalTo(expectedHeader.hex.take(10))
      output.drop(14) must be equalTo(expectedHeader.hex.drop(14))
    }
    
    "write date and time in stream" in new FileEntryContext {
      val entry = new Zip64LocalFileEntry(fileName, fileSize, 0, data)
      
      val output = new Array[Byte](localFileHeaderSize)
      
      val now = Calendar.getInstance()
      
      val n = entry.stream.read(output)
      val timeBytes = output.slice(10, 12)
      val dateBytes = output.slice(12, 14)

      val dosTime = bytesToInt(timeBytes) 
      val dosDate = bytesToInt(dateBytes)
      
      val fileTime = DosDate.toCalendar(dosDate, dosTime)

      now.getTimeInMillis must be closeTo(fileTime.getTimeInMillis, 5000)

    }
    
    "write file name" in new FileEntryContext {
      val fileData = Array.tabulate(fileSize)(_.toByte)
      val fileStream = new StoredInputStream(new ByteArrayInputStream(fileData))
      
      val entry = new Zip64LocalFileEntry(fileName, fileSize, 0, fileStream)
      
      val output = readStream(entry.stream)
      
      output.slice(localFileHeaderSize, localFileHeaderSize + fileName.size) must be equalTo fileName.getBytes
    }
    
    "write file" in new FileEntryContext {
      val fileData = Array.tabulate(fileSize)(_.toByte)
      val fileStream = new StoredInputStream(new ByteArrayInputStream(fileData))
      
      val entry = new Zip64LocalFileEntry(fileName, fileSize, 0, fileStream)

      val output = readStream(entry.stream)
      
      output.drop(localFileHeaderSize + fileName.size) must be equalTo fileData
    }
    
    trait FileEntryContext extends Scope {
      val fileName = "1234567890"
      
      val localFileHeaderSize = 30
      val extraFieldSize = 32
      val dataDescriptorSize = 24

      val fileSize = 100
      val data = mock[CRCInputStream]
      
      def entrySize(fileNameSize: Int) = localFileHeaderSize + extraFieldSize + dataDescriptorSize + fileSize + fileNameSize
      
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