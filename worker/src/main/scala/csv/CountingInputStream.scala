package csv

import java.io.FilterInputStream
import java.io.InputStream

class CountingInputStream(stream: InputStream) extends FilterInputStream(stream) {
  
  private var bytesReadCount: Long = 0l
  private var bytesReadAtMark = 0l
  
  def bytesRead: Long = bytesReadCount
  
  override def read(): Int = {
    val b = super.read
    if (b != -1) bytesReadCount += 1
    b
  }
  
  // called be read(b)
  override def read(b: Array[Byte], offset: Int, len: Int): Int = {
    val n = super.read(b, offset, len)
    if (n != -1) bytesReadCount += n
    n
  }

  override def skip(n: Long): Long = {
    val s = super.skip(n)
    if (s != -1) bytesReadCount += s
    s
  }
  
  override def mark(readlimit: Int) {
    super.mark(readlimit)
    bytesReadAtMark = bytesRead
  }
  
  override def reset() {
    super.reset
    bytesReadCount = bytesReadAtMark
  }
  
}