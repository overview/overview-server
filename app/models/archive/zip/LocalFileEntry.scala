package models.archive.zip

import models.archive.ArchiveEntry
import java.io.InputStream
import java.io.ByteArrayInputStream
import java.util.zip.CRC32
import java.nio.charset.StandardCharsets
import models.archive.ComposedInputStream
import models.archive.DosDate
import java.util.Calendar

class LocalFileEntry(entry: ArchiveEntry) extends ZipFormat with ZipFormatSize with LittleEndianWriter {

  def crc: Int = getOrComputeCrc

  def stream: InputStream = new ComposedInputStream(
      headerStream _,
      fileNameStream _)

  val size: Long = localFileHeader + fileNameBytes.size + entry.size

  protected val timeStamp = DosDate(Calendar.getInstance())
  
  private def fileNameBytes = entry.name.getBytes(StandardCharsets.UTF_8)
  private def fileNameLength = fileNameBytes.size

  private def fileNameStream: InputStream = new ByteArrayInputStream(fileNameBytes) 
  
  private def headerStream: InputStream = new ByteArrayInputStream(
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
      writeShort(empty))

  private var crcValue: Option[Int] = None

  private def getOrComputeCrc: Int = crcValue.getOrElse {
    crcValue = Some(computeCrc(entry.data()))
    crcValue.get
  }

  private val BufferSize = 8192

  private def computeCrc(input: InputStream): Int = {
    val buffer = new Array[Byte](BufferSize)
    val checker = new CRC32()

    def checkStream: Int = {
      val n = input.read(buffer)
      if (n == -1) checker.getValue.toInt
      else {
        checker.update(buffer.take(n))
        checkStream
      }
    }

    checkStream
  }

}