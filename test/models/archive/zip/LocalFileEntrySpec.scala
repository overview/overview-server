package models.archive.zip

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import java.io.InputStream
import java.io.ByteArrayInputStream
import models.archive.ArchiveEntry
import java.util.zip.CRC32

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
    
    "write values to stream" in {
      todo
    }

  }

}

trait LocalFileContext extends Scope {
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
}