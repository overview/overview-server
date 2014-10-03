package models.archive.streamingzip

import java.io.InputStream
import java.io.SequenceInputStream
import java.util.Calendar
import scala.collection.JavaConverters._
import models.archive.Archive
import models.archive.ArchiveEntry

/**
 * An archive in Zip64 format. All entries are written in Zip64 format.
 * @todo handle 64k entries
 */
class Zip64Archive(entries: Iterable[ArchiveEntry]) extends Archive(entries) {

  private val localFileHeaders = createLocalFileHeaders
  private val centralFileHeaders = createCentralFileHeaders
  private val zip64EndOfCentralDirectoryRecord = createZip64EndOfCentralDirectoryRecord
  private val zip64EndOfCentralDirectoryLocator = createZip64EndOfCentralDirectoryLocator
  private val endOfCentralDirectoryRecord = createEndOfCentralDirectoryRecord
  
  private val centralDirectory = createCentralDirectory(localFileHeaders)


  override def stream: InputStream = combineStreams(
    combineStreams(localFileHeaders.map(_.stream): _*),
    combineStreams(centralFileHeaders.map(_.stream): _*),
    zip64EndOfCentralDirectoryRecord.stream,
    zip64EndOfCentralDirectoryLocator.stream,
    endOfCentralDirectoryRecord.stream
  )
  
  private def combineStreams(streams: InputStream *): InputStream = 
    new SequenceInputStream(streams.iterator.asJavaEnumeration)
  
  private def createLocalFileHeaders: Seq[Zip64LocalFileEntry] =
    entries.map(Zip64LocalFileEntry(_)).toSeq

  private def createCentralFileHeaders: Seq[Zip64CentralFileHeader] = {
    val (_, headers) = localFileHeaders.foldLeft((0l, Seq.empty[Zip64CentralFileHeader])) { (result, l) =>
      val offset = result._1
      val now = DosDate(Calendar.getInstance())
      
      val centralFileHeader = new Zip64CentralFileHeader(l.fileName, l.fileSize, offset, now, l.crc32)
      (offset + l.size, result._2 :+ centralFileHeader)
    }
    
    headers
  }

  private def createZip64EndOfCentralDirectoryRecord: Zip64EndOfCentralDirectoryRecord = 
    new Zip64EndOfCentralDirectoryRecord(localFileHeaders, centralFileHeaders)
  
  private def createZip64EndOfCentralDirectoryLocator: Zip64EndOfCentralDirectoryLocator = 
    new Zip64EndOfCentralDirectoryLocator(localFileHeaders, centralFileHeaders)
  
  private def createEndOfCentralDirectoryRecord: EndOfCentralDirectoryRecord =
    new EndOfCentralDirectoryRecord
    
  private def createCentralDirectory(fileHeaders: Iterable[Zip64LocalFileEntry]) =
    new Zip64CentralDirectory(fileHeaders)
}



