package models.archive

import org.overviewproject.models.File
import java.io.InputStream
import controllers.util.PlayLargeObjectInputStream
import models.OverviewDatabase
import org.overviewproject.models.tables.Files
import org.overviewproject.models.tables.Pages
import org.overviewproject.database.Slick.simple._
import scala.slick.jdbc.GetResult.GetLong
import scala.slick.jdbc.StaticQuery
import java.io.ByteArrayInputStream

class ArchiveEntryFactoryWithStorage extends ArchiveEntryFactory {

  val storage = new Storage {
    override def findFile(fileId: Long): Option[File] =
      OverviewDatabase.withSlickSession { implicit session =>
        Files.filter(f => f.id === fileId).firstOption
      }

    override def findPageSize(pageId: Long): Option[Long] =
      OverviewDatabase.withSlickSession { implicit session =>
        val q = s"SELECT octet_length(data) FROM page WHERE id = $pageId"

        StaticQuery.queryNA(q).firstOption
      }

    override def largeObjectInputStream(oid: Long): InputStream = new PlayLargeObjectInputStream(oid)
    
    override def pageDataStream(pageId: Long): Option[InputStream] =
      OverviewDatabase.withSlickSession { implicit session =>
        val q = Pages.filter(_.id === pageId).map(_.data)
        q.firstOption.map(new ByteArrayInputStream(_))
      }

  }
}