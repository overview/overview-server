package models.archive.streamingzip

import models.archive.CRCInputStream
import java.io.InputStream

class StoredInputStream(in: InputStream) extends CRCInputStream(in) {
  def crc32: Long = 0
}