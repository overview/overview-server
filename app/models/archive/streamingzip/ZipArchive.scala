package models.archive.streamingzip

import models.archive.ArchiveEntry
import models.archive.Archive
import java.io.InputStream
import java.io.SequenceInputStream
import scala.collection.JavaConverters._
import java.util.Calendar

class ZipArchive(entries: Iterable[ArchiveEntry]) extends Archive(entries) {

  private val localFileHeaders = createLocalFileHeaders
  private val centralFileHeaders = createCentralFileHeaders
  private val endOfCentralDirectoryRecord = createEndOfCentralDirectoryRecord
  
  override val stream: InputStream = combineStreams(
    combineStreams(localFileHeaders.map(_.stream): _*),
    combineStreams(centralFileHeaders.map(_.stream): _*),
    endOfCentralDirectoryRecord.stream)

  private def createLocalFileHeaders: Seq[LocalFileEntry] =
    entries.map(e => new LocalFileEntry(e.name, e.size, e.data())).toSeq

  private def createCentralFileHeaders: Seq[CentralFileHeader] = {
    val (_, headers) = localFileHeaders.foldLeft((0l, Seq.empty[CentralFileHeader])) { (result, l) =>
      val offset = result._1
      val now = DosDate(Calendar.getInstance())

      val centralFileHeader = new CentralFileHeader(l.fileName, l.fileSize, offset, now, l.crc32)
      (offset + l.size, result._2 :+ centralFileHeader)
    }

    headers
  }
  
  private def createEndOfCentralDirectoryRecord: EndOfCentralDirectoryRecord = 
    new EndOfCentralDirectoryRecord(localFileHeaders, centralFileHeaders)

  private def combineStreams(streams: InputStream*): InputStream =
    new SequenceInputStream(streams.iterator.asJavaEnumeration)

}