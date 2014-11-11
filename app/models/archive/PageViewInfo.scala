package models.archive

import java.io.InputStream

case class PageViewInfo(documentTitle: String, pageNumber: Int, pageId: Long, size: Long)

abstract class PageViewInfo1(documentTitle: String, pageNumber: Int, pageId: Long, size: Long) extends DocumentViewInfo {

  def archiveEntry: ArchiveEntry = 
    ArchiveEntry(fileNameWithPage(removePdf(documentTitle), pageNumber), size, pageDataStream(pageId) _)

    
  private def fileNameWithPage(fileName: String, pageNumber: Int): String =
    asPdf(s"$fileName p$pageNumber")

    
  private def pageDataStream(pageId: Long)(): InputStream =
    storage.pageDataStream(pageId).get

  protected val storage: Storage
  protected trait Storage {
    def pageDataStream(pageId: Long): Option[InputStream]
  }
}
