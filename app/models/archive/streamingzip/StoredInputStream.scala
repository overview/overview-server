package models.archive.streamingzip

import models.archive.CRCInputStream
import java.io.InputStream
import java.util.zip.CRC32

/**
 * [[InputStream]] containing data to be stored uncompressed in zip file.
 * Provides a CRC32 value for bytes read so far.
 */
class StoredInputStream(in: InputStream) extends CRCInputStream(in) {
  private val checker = new CRC32

  def crc32: Long = checker.getValue

  override def read(): Int =
    readAndCheck(in.read, checker.update)

  override def read(b: Array[Byte]): Int =
    readAndCheck(in.read(b), n => checker.update(b, 0, n))

  override def read(b: Array[Byte], offset: Int, len: Int): Int =
    readAndCheck(in.read(b, offset, len), n => checker.update(b, offset, n))

  override def markSupported: Boolean = false

  private def readAndCheck(readFn: => Int, checkFn: Int => Unit): Int = {
    val n = readFn

    if (n != -1) checkFn(n) 

    n
  }
}