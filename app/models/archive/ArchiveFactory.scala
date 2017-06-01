package models.archive

import javax.inject.Inject

import controllers.backend.ArchiveEntryBackend // TODO rename controllers.backend to models.backend
import models.ArchiveEntry

class ArchiveFactory @Inject() (
  val archiveEntryBackend: ArchiveEntryBackend
) {
  private val MaxNumberOfEntries: Int = 0xFFFF // If more than 2 bytes are needed for entries, ZIP64 should be used
  private val MaxArchiveSize: Long = 0xFFFFFFFFL // If more than 4 bytes are needed for size, ZIP64 should be used

  def createZip(documentSetId: Long, entries: Seq[ArchiveEntry]): Either[String,ZipArchive] = {
    if (entries.isEmpty) {
      Left("empty")
    } else {
      val ret = new ZipArchive(documentSetId, entries, archiveEntryBackend)
      if (ret.size > MaxArchiveSize) {
        Left("archiveTooLarge")
      } else if (ret.archiveEntries.length > MaxNumberOfEntries) {
        Left("tooManyEntries")
      } else {
        Right(ret)
      }
    }
  }
}
