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

object PageViewInfo1 {
  import java.io.ByteArrayInputStream
  import models.OverviewDatabase
  import org.overviewproject.database.Slick.simple._
  import org.overviewproject.models.tables.Pages

  def apply(documentTitle: String, pageNumber: Int, pageId: Long, size: Long): PageViewInfo1 =
    new DbPageViewInfo(documentTitle, pageNumber, pageId, size)

  private class DbPageViewInfo(documentTitle: String, pageNumber: Int, pageId: Long, size: Long)
      extends PageViewInfo1(documentTitle, pageNumber, pageId, size) {

    override protected val storage = new DbStorage

    protected class DbStorage extends Storage {
      override def pageDataStream(pageId: Long): Option[InputStream] =
        OverviewDatabase.withSlickSession { implicit session =>
          val q = Pages.filter(_.id === pageId).map(_.data)
          q.firstOption.map(new ByteArrayInputStream(_))
        }
    }
  }
}