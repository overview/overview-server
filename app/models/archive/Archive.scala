package models.archive

import java.io.InputStream

abstract class Archive(entries: Iterable[ArchiveEntry]) {
  def archiveSize: Long
  
  def stream: InputStream
}