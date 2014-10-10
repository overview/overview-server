package models.archive.zip

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import java.io.InputStream
import java.io.ByteArrayInputStream
import models.archive.ArchiveEntry
import java.util.zip.CRC32
import models.archive.StreamReader
import java.util.Calendar
import java.nio.ByteBuffer
import java.nio.ByteOrder._
import models.archive.DosDate

class LocalFileEntrySpec extends Specification {

  "LocalFileEntry" should {

    "read stream to set crc when accessed" in new LocalFileContext {
      streamRequestedCount must be equalTo 0

      localFileEntry.crc must be equalTo crc

      streamRequestedCount must be equalTo 1
    }

    "only read stream once to set crc" in new LocalFileContext {
      localFileEntry.crc must be equalTo crc
      localFileEntry.crc must be equalTo crc

      streamRequestedCount must be equalTo 1
    }

    "return size" in new LocalFileContext {
      localFileEntry.size must be equalTo (headerSize + fileName.size + numberOfBytes)
    }

    "write header to stream" in new LocalFileContext {
      val expectedHeader =
        writeInt(0x04034b50) ++
          writeShort(10) ++
          writeShort(0x0800) ++
          writeShort(0) ++
          writeShort(0) ++
          writeShort(0) ++
          writeInt(crc) ++
          writeInt(numberOfBytes) ++
          writeInt(numberOfBytes) ++
          writeShort(fileName.size) ++
          writeShort(0)

      val output = readStream(localFileEntry.stream)

      // Don't check time and date values
      output.take(10) must be equalTo expectedHeader.take(10)
      output.slice(14, headerSize) must be equalTo expectedHeader.drop(14)
    }

    "write date and time in stream" in new LocalFileContext {
      val output = readStream(localFileEntry.stream)

      val now = Calendar.getInstance()

      val timeBytes = output.slice(10, 12)
      val dateBytes = output.slice(12, 14)

      val dosTime = bytesToInt(timeBytes)
      val dosDate = bytesToInt(dateBytes)

      val fileTime = DosDate.toCalendar(dosDate, dosTime)

      now.getTimeInMillis must be closeTo (fileTime.getTimeInMillis, 5000)
    }
    
    "write filename in stream" in new LocalFileContext {
      val output = readStream(localFileEntry.stream)
      
      output.slice(headerSize, headerSize + fileName.size) must be equalTo fileName.getBytes
    }

    "write file in stream" in new LocalFileContext {
      val output = readStream(localFileEntry.stream)
      
      output.drop(headerSize + fileName.size) must be equalTo data
    }
  }

}

trait LocalFileContext extends Scope with LittleEndianWriter with StreamReader {
  val headerSize = 30
  val numberOfBytes = 52
  val data = Array.range(1, numberOfBytes).map(_.toByte)
  val crc = {
    val checker = new CRC32()

    checker.update(data)
    checker.getValue.toInt
  }
  var streamRequestedCount = 0

  def stream(): InputStream = {
    streamRequestedCount += 1
    new ByteArrayInputStream(data)
  }

  val fileName = "fileName"

  val archiveEntry = ArchiveEntry(numberOfBytes, fileName, stream)
  val localFileEntry = new LocalFileEntry(archiveEntry)

  // assumes 2 bytes in array
  def bytesToInt(bytes: Array[Byte]): Int = {
    val byteBuffer = ByteBuffer.allocate(4).order(LITTLE_ENDIAN)

    byteBuffer.put(bytes)

    byteBuffer.getShort(0)
  }

}