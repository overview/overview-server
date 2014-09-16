package models.archive

import java.io.InputStream

abstract class ArchiveStream(entries: Iterable[ArchiveEntry]) extends InputStream {
  def streamLength: Long
  
  
}