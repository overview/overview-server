package models.archive.streamingzip

import models.archive.CRCInputStream
import java.io.InputStream
import java.util.zip.CRC32

class StoredInputStream(in: InputStream) extends CRCInputStream(in) {
  private val checker = new CRC32
  
  def crc32: Long = checker.getValue
  
  override def read(): Int = {
    val b = in.read
    
    if (b != -1) checker.update(b)
    
    b
  }
  
  override def read(b: Array[Byte]): Int = {
    val numberOfBytesRead = in.read(b)
    
    if (numberOfBytesRead != -1) checker.update(b)
    
    numberOfBytesRead
  }
}