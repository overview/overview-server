package models.archive.zip

import models.archive.ArchiveEntry

class LocalFileEntry(entry: ArchiveEntry)  {

  def crc: Option[Int] = None
}