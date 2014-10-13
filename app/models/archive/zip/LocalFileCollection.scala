package models.archive.zip

import models.archive.ArchiveEntry
import models.archive.ComposedInputStream
import java.io.InputStream
import models.archive.ComposedInputStream

class LocalFileCollection(archiveEntries: Seq[ArchiveEntry]) {
  val entries: Seq[LocalFileEntry] = createEntries

  def stream: InputStream = new ComposedInputStream(entries.map(_.stream _): _*)
  
  private def createEntries: Seq[LocalFileEntry] = {
    val (_, newEntries) = archiveEntries.foldLeft((0l, Seq.empty[LocalFileEntry])) { (r, e) =>
      val (currentOffset, createdEntries) = r

      val newEntry = new LocalFileEntry(e, currentOffset)
      val nextOffset = currentOffset + newEntry.size

      (nextOffset, createdEntries :+ newEntry)
    }

    newEntries
  }
}