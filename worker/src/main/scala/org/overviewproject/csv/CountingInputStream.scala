/*
 * CountingInputStream.scala
 * 
 * Overview Project
 * Created by Jonas Karlsson, November 2012
 */
package org.overviewproject.csv

import java.io.FilterInputStream
import java.io.InputStream

/**
 * An InputStream that tracks the number of bytes read, allowing
 * progress to be reported.
 */
class CountingInputStream(stream: InputStream) extends FilterInputStream(stream) {
  var bytesRead: Long = 0L

  override def read(): Int = {
    val b = in.read
    if (b != -1) bytesRead += 1
    b
  }

  override def read(b: Array[Byte], offset: Int, len: Int): Int = {
    val n = in.read(b, offset, len)
    if (n != -1) bytesRead += n
    n
  }
}
