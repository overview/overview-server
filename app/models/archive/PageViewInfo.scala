package models.archive

import play.api.libs.iteratee.Enumerator
import scala.concurrent.Future

import org.overviewproject.blobstorage.BlobStorage

abstract class PageViewInfo(
  documentTitle: String,
  pageNumber: Int,
  pageId: Long,
  override val size: Long
) extends DocumentViewInfo {
  override def name = fileNameWithPage(removePdf(documentTitle), pageNumber)

  private def fileNameWithPage(fileName: String, pageNumber: Int): String =
    asPdf(addPageNumber(fileName, pageNumber))
}

object PageViewInfo {
  def apply(documentTitle: String, pageNumber: Int, pageId: Long, size: Long): PageViewInfo =
    new BlobStoragePageViewInfo(documentTitle, pageNumber, pageId, size)

  private class BlobStoragePageViewInfo(documentTitle: String, pageNumber: Int, pageId: Long, size: Long)
      extends PageViewInfo(documentTitle, pageNumber, pageId, size) {
    override def stream = BlobStorage.get("pagebytea:" + pageId)
  }
}
