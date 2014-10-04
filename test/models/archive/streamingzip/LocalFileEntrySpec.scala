package models.archive.streamingzip

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder._
import java.util.zip.CRC32
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import java.util.Calendar

class LocalFileEntrySpec extends Specification {

  "write header" in new LocalFileEntryContext {
    val output = readStream(entry.stream)

    val expectedHeader = {
      writeInt(0x04034b50) ++
        writeShort(10) ++
        writeShort(0x0808) ++
        writeShort(0) ++
        writeShort(0) ++
        writeShort(0) ++
        writeInt(0) ++
        writeInt(0) ++
        writeInt(0) ++
        writeShort(fileName.length) ++
        writeShort(0)
    }

    output.take(10) must be equalTo expectedHeader.take(10)
    output.slice(14, localFileHeaderSize) must be equalTo expectedHeader.drop(14)
  }

  "write date and time in stream" in new LocalFileEntryContext {
    val output = readStream(entry.stream)

    val now = Calendar.getInstance()

    val timeBytes = output.slice(10, 12)
    val dateBytes = output.slice(12, 14)

    val dosTime = bytesToInt(timeBytes)
    val dosDate = bytesToInt(dateBytes)

    val fileTime = DosDate.toCalendar(dosDate, dosTime)

    now.getTimeInMillis must be closeTo (fileTime.getTimeInMillis, 5000)

  }

  "write filename" in new LocalFileEntryContext {
    val output = readStream(entry.stream)

    output.slice(localFileHeaderSize, localFileHeaderSize + fileName.size) must be equalTo fileName.getBytes

  }

  "write file" in new LocalFileEntryContext {
    val output = readStream(entry.stream)

    output.slice(localFileHeaderSize + fileName.size,
      localFileHeaderSize + fileName.size + fileSize) must be equalTo fileData
  }

  "write data descriptor" in new LocalFileEntryContext {
    val output = readStream(entry.stream)

    val expectedDataDescriptor =
      writeInt(0x08074b50) ++ // signature
        writeInt(crc32.toInt) ++ // crc32
        writeLong(fileSize) ++ // original size
        writeLong(fileSize) // compressed size 	

    output.drop(localFileHeaderSize + fileName.size + fileSize) must be equalTo expectedDataDescriptor
  }

  trait LocalFileEntryContext extends Scope with LittleEndianWriter {
    val fileName = "1234567890"

    val localFileHeaderSize = 30

    val fileSize = 100
    val fileData = Array.tabulate(fileSize)(_.toByte)
    val fileStream = new ByteArrayInputStream(fileData)
    val crc32 = {
      val checker = new CRC32()

      checker.update(fileData)
      checker.getValue
    }

    val entry = new LocalFileEntry(fileName, fileSize, fileStream)

    def readStream(stream: InputStream): Array[Byte] =
      Stream.continually(stream.read).takeWhile(_ != -1).map(_.toByte).toArray

    // assumes 2 bytes in array
    def bytesToInt(bytes: Array[Byte]): Int = {
      val byteBuffer = ByteBuffer.allocate(4).order(LITTLE_ENDIAN)

      byteBuffer.put(bytes)

      byteBuffer.getShort(0)
    }

  }

}