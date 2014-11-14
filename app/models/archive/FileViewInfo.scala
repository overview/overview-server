package models.archive

import java.io.InputStream


abstract class FileViewInfo(documentTitle: String, viewOid: Long, size: Long) extends DocumentViewInfo {

  def archiveEntry: ArchiveEntry =
    ArchiveEntry(asPdf(removePdf(documentTitle)), size, largeObjectInputStream(viewOid) _)

  private def largeObjectInputStream(oid: Long)(): InputStream = storage.largeObjectInputStream(oid)

  protected val storage: Storage
  protected trait Storage {
    def largeObjectInputStream(oid: Long): InputStream
  }
}

object FileViewInfo {
  import controllers.util.PlayLargeObjectInputStream

  private val LOBufferSize = 1024 * 1024
  
  def apply(documentTitle: String, viewOid: Long, size: Long): FileViewInfo =
    new DbFileViewInfo(documentTitle, viewOid, size)

  private class DbFileViewInfo(documentTitle: String, viewOid: Long, size: Long)
      extends FileViewInfo(documentTitle, viewOid, size) {

    override protected val storage = new DbStorage

    protected class DbStorage extends Storage {
      override def largeObjectInputStream(oid: Long): InputStream = new PlayLargeObjectInputStream(oid, LOBufferSize)
    }
  }
}