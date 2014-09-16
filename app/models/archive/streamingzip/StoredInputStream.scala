package models.archive.streamingzip

import models.archive.CRCInputStream
import java.io.InputStream
import java.util.zip.CRC32

class StoredInputStream(in: InputStream) extends CRCInputStream(in) {
  private val checker = new CRC32
  
  def crc32: Long = checker.getValue
  
  override def read(): Int =
    readAndCheck(in.read, checker.update)
  
  override def read(b: Array[Byte]): Int = 
    readAndCheck(in.read(b), _ => checker.update(b))
    
  override def read(b: Array[Byte], offset: Int, len: Int): Int = 
    readAndCheck(in.read(b, offset, len), _ => checker.update(b, offset, len))
    
  override def markSupported: Boolean = false
  
  private def readAndCheck(readFn: => Int, checkFn: Int => Unit): Int = {
    val n = readFn
    
    if (n != -1) checkFn(n)
    
    n
  }
}