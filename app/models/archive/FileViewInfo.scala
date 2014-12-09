package models.archive

import play.api.libs.iteratee.Enumerator
import scala.concurrent.Future

import org.overviewproject.blobstorage.BlobStorage

abstract class FileViewInfo(
  documentTitle: String,
  viewOid: Long,
  override val size: Long
) extends DocumentViewInfo {
  override def name = asPdf(removePdf(documentTitle))
}

object FileViewInfo {
  private class BlobStorageFileViewInfo(
    documentTitle: String,
    viewOid: Long,
    size: Long,
    private val blobStorage: BlobStorage
  ) extends FileViewInfo(documentTitle, viewOid, size) {
    override def stream() = blobStorage.get("pglo:" + viewOid)
  }

  def apply(documentTitle: String, viewOid: Long, size: Long): FileViewInfo =
    new BlobStorageFileViewInfo(documentTitle, viewOid, size, BlobStorage)
}
