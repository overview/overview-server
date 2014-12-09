package models.archive.zip

import play.api.libs.iteratee.{Enumeratee,Enumerator,Input}
import scala.concurrent.Future

import models.archive.ArchiveEntry

class LocalFileCollection(archiveEntries: Seq[ArchiveEntry]) {
  val entries: Seq[LocalFileEntry] = createEntries

  val size: Long = entries.map(_.size).sum

  def stream: Enumerator[Array[Byte]] = {
    import play.api.libs.iteratee.Execution.Implicits.defaultExecutionContext

    val flattener = Enumeratee.mapFlatten[LocalFileEntry][Array[Byte]](_.stream)

    Enumerator.enumerate(entries).through(flattener)
  }

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
