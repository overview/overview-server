package models.archive.streamingzip

import models.archive.ArchiveEntry
import java.nio.charset.StandardCharsets

object ArchiveSize {

  def apply(entries: Seq[ArchiveEntry]): Long =
    entries.map(e => localFileHeaderSize(e) + centralDirectorySize(e)).sum +
      zip64EndOfCentralDirectorySize +
      zip64EndOfCentralDirectoryLocatorSize +
      endOfCentralDirectorySize

  private def localFileHeaderSize(entry: ArchiveEntry): Long =
    localFileHeaderSize +
      entry.name.getBytes(StandardCharsets.UTF_8).length +
      localFileHeaderExtraFieldSize +
      entry.size +
      dataDescriptorSize

  private def centralDirectorySize(entry: ArchiveEntry): Long =
    centralDirectoryHeaderSize +
      entry.name.getBytes(StandardCharsets.UTF_8).length +
      centralDirectoryExtraFieldSize

  private val localFileHeaderSize = 30
  private val localFileHeaderExtraFieldSize = 20
  private val dataDescriptorSize = 24
  private val centralDirectoryHeaderSize = 46
  private val centralDirectoryExtraFieldSize = 28
  private val zip64EndOfCentralDirectorySize = 56
  private val zip64EndOfCentralDirectoryLocatorSize = 20
  private val endOfCentralDirectorySize = 22

}