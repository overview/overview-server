package models.archive

import java.io.FilterInputStream
import java.io.InputStream

abstract class CRCInputStream(in: InputStream) extends FilterInputStream(in) {
  def crc32: Long 
}