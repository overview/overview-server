package models.archive.zip

import models.archive.ArchiveEntry
import java.io.InputStream
import java.io.ByteArrayInputStream
import java.util.zip.CRC32

class LocalFileEntry(entry: ArchiveEntry) {

  def crc: Int = getOrComputeCrc

  def stream: InputStream = {

    new ByteArrayInputStream(Array.empty)
  }

  private val BufferSize = 8192

  private var crcValue: Option[Int] = None

  private def getOrComputeCrc: Int = crcValue.getOrElse {
    crcValue = Some(computeCrc(entry.data()))
    crcValue.get
  }

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