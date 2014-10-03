package models.archive.streamingzip

import models.archive.ArchiveEntry
import java.nio.charset.StandardCharsets

object ArchiveSize extends ZipFormatSize {

  def apply(entries: Seq[ArchiveEntry]): Long =
    entries.map(e => localFileHeaderSize(e) + centralDirectorySize(e)).sum +
      zip64EndOfCentralDirectory +
      zip64EndOfCentralDirectoryLocator +
      endOfCentralDirectory

  private def localFileHeaderSize(entry: ArchiveEntry): Long =
    localFileHeader +
      nameSize(entry) +
      localFileHeaderExtraField +
      entry.size +
      dataDescriptor

  private def centralDirectorySize(entry: ArchiveEntry): Long =
    centralDirectoryHeader +
      nameSize(entry) +
      centralDirectoryExtraField

  private def nameSize(entry: ArchiveEntry): Long = entry.name.getBytes(StandardCharsets.UTF_8).length
}