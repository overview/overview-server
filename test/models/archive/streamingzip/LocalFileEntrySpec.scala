package models.archive.streamingzip

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder._
import java.util.zip.CRC32
import org.specs2.mutable.Specification
import org.specs2.specification.Scope


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
    output.drop(14) must be equalTo expectedHeader.drop(14)
  }

  "write date and time in stream" in new LocalFileEntryContext {
    todo
  }

  "write filename" in new LocalFileEntryContext {
    todo
  }

  "write file" in new LocalFileEntryContext {
    todo
  }

  "write data descriptor" in new LocalFileEntryContext {
    todo
  }

  trait LocalFileEntryContext extends Scope with LittleEndianWriter {
    val fileName = "1234567890"

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
  }

}