package models.archive.streamingzip

import models.archive.CRCInputStream
import models.archive.ArchiveEntry
import java.nio.charset.StandardCharsets

case class Zip64LocalFileEntry(fileName: String, fileSize: Long, data: CRCInputStream) {
  private val HeaderSize = 30
  private val ExtraFieldSize = 32
  private val DataDescriptorSize = 24

  def size: Long = HeaderSize + fileNameSize + ExtraFieldSize + fileSize + DataDescriptorSize

  private def fileNameSize = fileName.getBytes(StandardCharsets.UTF_8).size
}

object Zip64LocalFileEntry {
  def apply(entry: ArchiveEntry): Zip64LocalFileEntry =
    Zip64LocalFileEntry(entry.name, entry.size, entry.data)
}