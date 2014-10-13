package models.archive.zip

import java.nio.charset.StandardCharsets
import models.archive.ComposedInputStream

class CentralDirectory(localFileEntries: Seq[LocalFileEntry]) extends ZipFormatSize {

  val size = localFileEntries.map(e => centralDirectoryHeader + fileNameSize(e.fileName)).sum
  val offset = localFileEntries.map(_.size).sum

  def stream = {
    val centralFileHeaderStreams = localFileEntries.map { e =>
      val header = new CentralDirectoryFileHeader(e.fileName, e.fileSize, e.crc, e.offset, e.timeStamp)
      header.stream _
    }
      
    new ComposedInputStream(centralFileHeaderStreams: _*)
  }
  
  private def fileNameBytes(name: String) = name.getBytes(StandardCharsets.UTF_8)
  private def fileNameSize(name: String) = fileNameBytes(name).size
}