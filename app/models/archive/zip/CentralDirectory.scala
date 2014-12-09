package models.archive.zip

import java.nio.charset.StandardCharsets
import play.api.libs.iteratee.Enumerator

import models.archive.ComposedInputStream

class CentralDirectory(localFileEntries: Seq[LocalFileEntry]) extends ZipFormatSize {
  val size = localFileEntries.map(e => centralDirectoryHeader + fileNameSize(e.fileName)).sum
  val offset = localFileEntries.map(_.size).sum

  def bytes: Array[Byte] = {
    localFileEntries
      .map { e =>
        // At this point, we assume the LocalFileEntry has been streamed. That
        // means its crcFuture has been resolved. (If we're wrong, crash hard so
        // we get emailed about it.)
        val crc = e.crcFuture.value.get.get
        new CentralDirectoryFileHeader(e.fileName, e.fileSize, crc, e.offset, e.timeStamp)
      }             // Seq[CentralDirectoryFileHeader]
      .map(_.bytes) // Seq[Array[Byte]]
      .toArray.flatten
  }
  
  private def fileNameBytes(name: String) = name.getBytes(StandardCharsets.UTF_8)
  private def fileNameSize(name: String) = fileNameBytes(name).size
}
