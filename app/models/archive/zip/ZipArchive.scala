package models.archive.zip

import play.api.libs.iteratee._
import scala.concurrent.Future

import models.archive.Archive
import models.archive.ArchiveEntryCollection

class ZipArchive(entryCollection: ArchiveEntryCollection) extends Archive  {
  override def stream: Enumerator[Array[Byte]] = {
    /*
     * localFileCollection.stream calculates CRCs as a side-effect. We want to
     * calculate the central directory after we're sure that's finished. Let's
     * calculate it when the client asks for it as the last part of the stream.
     */
    import play.api.libs.iteratee.Execution.Implicits.defaultExecutionContext
    val delayedCentralDirectory = new Enumerator[Array[Byte]] {
      override def apply[A](i: Iteratee[Array[Byte], A]): Future[Iteratee[Array[Byte], A]] = i.pureFold {
        case Step.Cont(k) => k(Input.El(centralDirectory.bytes))
        case _ => i
      }
    }

    localFileCollection.stream
      .andThen(delayedCentralDirectory)
      .andThen(Enumerator(endOfCentralDirectoryRecord.bytes))
  }

  override def size: Long = localFileCollection.size + centralDirectory.size + endOfCentralDirectoryRecord.size

  private val entries = entryCollection.sanitizedEntries

  private val localFileCollection = new LocalFileCollection(entries)
  private val centralDirectory = new CentralDirectory(localFileCollection.entries)
  private val endOfCentralDirectoryRecord = 
    new EndOfCentralDirectoryRecord(entries.size, centralDirectory.size, centralDirectory.offset) 
  
}
