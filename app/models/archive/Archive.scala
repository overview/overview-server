package models.archive

import java.io.InputStream

abstract class Archive(entries: Iterable[ArchiveEntry]) {

  def stream: InputStream
  def size: Long
}