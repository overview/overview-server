package models.archive.zip

import java.nio.charset.StandardCharsets
import java.util.Calendar
import java.util.zip.CRC32
import play.api.libs.iteratee.{Enumerator,Iteratee}
import scala.concurrent.Future

import models.archive.ArchiveEntry
import models.archive.DosDate

class LocalFileEntry(entry: ArchiveEntry, val offset: Long) extends ZipFormat with ZipFormatSize with LittleEndianWriter {
  private implicit val executionContext = play.api.libs.iteratee.Execution.Implicits.defaultExecutionContext

  lazy val crcFuture: Future[Int] = entry.data().flatMap { e => computeCrc(e) }

  def stream: Enumerator[Array[Byte]] = {
    // XXX we stream the documents twice! BOO.
    val head = crcFuture.map(crc => Enumerator(headerBytes(crc)))
    val body = entry.data()

    Enumerator.flatten(head)
      .andThen(Enumerator(fileNameBytes))
      .andThen(Enumerator.flatten(body))
  }

  val size: Long = localFileHeader + fileNameBytes.size + entry.size
  val fileName: String = entry.name
  val fileSize: Long = entry.size
  
  val timeStamp = DosDate(Calendar.getInstance())
  
  private def fileNameBytes = entry.name.getBytes(StandardCharsets.UTF_8)
  private def fileNameLength = fileNameBytes.size
  
  private def headerBytes(crc: Int): Array[Byte] = {
    writeInt(localFileEntrySignature) ++
      writeShort(defaultVersion) ++
      writeShort(useUTF8) ++
      writeShort(noCompression) ++
      writeShort(timeStamp.time) ++
      writeShort(timeStamp.date) ++
      writeInt(crc) ++
      writeInt(entry.size.toInt) ++
      writeInt(entry.size.toInt) ++
      writeShort(fileNameLength) ++
      writeShort(empty)
  }

  private def computeCrc(e: Enumerator[Array[Byte]]): Future[Int] = {
    val crc32 = new CRC32()
    val iteratee = Iteratee.foreach { (bytes: Array[Byte]) => crc32.update(bytes) }
    e.run(iteratee).map(_ => crc32.getValue.toInt)
  }
}
