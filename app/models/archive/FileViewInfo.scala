package models.archive

import java.io.InputStream

case class FileViewInfo(documentTitle: String, viewOid: Long, size: Long) 

abstract class FileViewInfo1(documentTitle: String, viewOid: Long, size: Long) extends DocumentViewInfo {

  def archiveEntry: ArchiveEntry =
    ArchiveEntry(asPdf(removePdf(documentTitle)), size, largeObjectInputStream(viewOid) _)

  private def largeObjectInputStream(oid: Long)(): InputStream = storage.largeObjectInputStream(oid)
  
  protected val storage: Storage
  protected trait Storage {
    def largeObjectInputStream(oid: Long): InputStream
  }
}