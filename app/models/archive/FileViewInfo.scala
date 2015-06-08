package models.archive

import play.api.libs.iteratee.Enumerator
import scala.concurrent.Future

import org.overviewproject.blobstorage.BlobStorage

abstract class FileViewInfo(
  val documentTitle: String,
  val location: String,
  override val size: Long
) extends DocumentViewInfo {
  override def name = asPdf(removePdf(documentTitle))

  override def equals(o: Any) = o match {
    case rhs: FileViewInfo => (
      documentTitle == rhs.documentTitle
      && location == rhs.location
      && size == rhs.size
    )
    case _ => false
  }
}

object FileViewInfo {
  private class BlobStorageFileViewInfo(
    documentTitle: String,
    location: String,
    size: Long,
    private val blobStorage: BlobStorage
  ) extends FileViewInfo(documentTitle, location, size) {
    override def stream() = blobStorage.get(location)
  }

  def apply(documentTitle: String, location: String, size: Long): FileViewInfo =
    new BlobStorageFileViewInfo(documentTitle, location, size, BlobStorage)
}
