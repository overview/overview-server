package models.archive.zip

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder._
import java.util.Calendar
import java.util.zip.CRC32
import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import play.api.libs.iteratee.{Enumerator,Iteratee}
import play.api.test.{DefaultAwaitTimeout,FutureAwaits}
import scala.concurrent.Future

import models.archive.ArchiveEntry
import models.archive.DosDate

class LocalFileEntrySpec extends Specification {

  "LocalFileEntry" should {

    "set CRC after streaming the data" in new LocalFileContext {
      await(localFileEntry.stream.run(Iteratee.consume()))
      await(localFileEntry.crcFuture) must be equalTo crc
    }

    "only read stream once to set crc" in new LocalFileContext {
      // This is a hack. Actually we stream twice, total: once (and only once)
      // to set the CRC -- that's this test -- and once to stream the data to
      // the caller. This test _really_ ought to check that we calculate the
      // CRC _while_ streaming to the caller... but we can't do that in the
      // current implementation because we put the CRC in the header, before we
      // begin streaming the file.
      await(localFileEntry.crcFuture)
      await(localFileEntry.crcFuture)

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

      val output = await(localFileEntry.stream.run(Iteratee.consume()))

      // Don't check time and date values
      output.take(10) must be equalTo expectedHeader.take(10)
      output.slice(14, headerSize) must be equalTo expectedHeader.drop(14)
    }

    "write date and time in stream" in new LocalFileContext {
      val output = await(localFileEntry.stream.run(Iteratee.consume()))

      val now = Calendar.getInstance()

      val timeBytes = output.slice(10, 12)
      val dateBytes = output.slice(12, 14)

      val dosTime = bytesToInt(timeBytes)
      val dosDate = bytesToInt(dateBytes)

      val fileTime = DosDate.toCalendar(dosDate, dosTime)

      now.getTimeInMillis must be closeTo (fileTime.getTimeInMillis, 5000)
    }
    
    "write filename in stream" in new LocalFileContext {
      val output = await(localFileEntry.stream.run(Iteratee.consume()))
      
      output.slice(headerSize, headerSize + fileName.size) must be equalTo fileName.getBytes
    }

    "write file in stream" in new LocalFileContext {
      val output = await(localFileEntry.stream.run(Iteratee.consume()))
      
      output.drop(headerSize + fileName.size) must be equalTo data
    }
  }

  trait LocalFileContext extends Scope with LittleEndianWriter with FutureAwaits with DefaultAwaitTimeout {
    val headerSize = 30
    val numberOfBytes = 52
    val data = Array.range(1, numberOfBytes).map(_.toByte)
    val crc = {
      val checker = new CRC32()

      checker.update(data)
      checker.getValue.toInt
    }
    var streamRequestedCount = 0

    def stream(): Future[Enumerator[Array[Byte]]] = {
      streamRequestedCount += 1
      Future.successful(Enumerator(data))
    }

    val fileName = "fileName"

    val archiveEntry = ArchiveEntry(fileName, numberOfBytes, stream _)
    val localFileEntry = new LocalFileEntry(archiveEntry, 0)

    // assumes 2 bytes in array
    def bytesToInt(bytes: Array[Byte]): Int = {
      val byteBuffer = ByteBuffer.allocate(4).order(LITTLE_ENDIAN)

      byteBuffer.put(bytes)

      byteBuffer.getShort(0)
    }

  }
}
