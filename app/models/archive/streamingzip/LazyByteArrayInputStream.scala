package models.archive.streamingzip

import java.io.InputStream
import java.io.ByteArrayInputStream

/**
 * An [[ByteArrayInputStream]] that lazily generates the underlying byte [[Array]]
 */
class LazyByteArrayInputStream(byteGenerator: => Array[Byte]) extends InputStream {

  override def read: Int = stream.read
  override def read(b: Array[Byte]): Int = stream.read(b)
  override def read(b: Array[Byte], offset: Int, len: Int): Int = stream.read(b, offset, len)
  
  override def available: Int = stream.available
  override def close: Unit = stream.close
  override def markSupported: Boolean = stream.markSupported
  override def mark(readLimit: Int): Unit = stream.mark(readLimit)
  override def reset: Unit = stream.reset
  override def skip(n: Long): Long = stream.skip(n)
  
  private def stream: InputStream = {
    actualStream.getOrElse {
      actualStream = Some(new ByteArrayInputStream(byteGenerator))
      
      actualStream.get
    }
  }
  private var actualStream: Option[InputStream] = None
}