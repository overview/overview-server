package models.archive

import play.api.libs.iteratee.Enumerator
import scala.concurrent.Future

import org.overviewproject.blobstorage.BlobStorage

abstract class PageViewInfo(
  val documentTitle: String,
  val pageNumber: Int,
  val dataLocation: String,
  override val size: Long
) extends DocumentViewInfo {
  override def name = fileNameWithPage(removePdf(documentTitle), pageNumber)

  private def fileNameWithPage(fileName: String, pageNumber: Int): String =
    asPdf(addPageNumber(fileName, pageNumber))

  override def equals(o: Any) = o match {
    case rhs: PageViewInfo => (
      documentTitle == rhs.documentTitle
      && pageNumber == rhs.pageNumber
      && dataLocation == rhs.dataLocation
      && size == rhs.size
    )
  }
}

object PageViewInfo {
  def apply(documentTitle: String, pageNumber: Int, dataLocation: String, size: Long): PageViewInfo =
    new BlobStoragePageViewInfo(documentTitle, pageNumber, dataLocation, size)

  private class BlobStoragePageViewInfo(documentTitle: String, pageNumber: Int, dataLocation: String, size: Long)
      extends PageViewInfo(documentTitle, pageNumber, dataLocation, size) {
    override def stream = BlobStorage.get(dataLocation)
  }
}
