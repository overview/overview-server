package models.archive

import java.io.InputStream


trait StreamReader {
  def readStream(stream: InputStream): Array[Byte] = {
    val readValues = Stream.continually(stream.read).takeWhile(_ != -1)
    readValues.map(_.toByte).toArray
  }
}
