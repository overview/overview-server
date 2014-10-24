package models.archive

import java.io.ByteArrayInputStream
import java.io.InputStream

import scala.slick.jdbc.StaticQuery.interpolation

import controllers.util.PlayLargeObjectInputStream
import models.OverviewDatabase
import org.overviewproject.database.Slick.simple._
import org.overviewproject.models.File
import org.overviewproject.models.tables.Files
import org.overviewproject.models.tables.Pages

class ArchiveEntryFactoryWithStorage extends ArchiveEntryFactory {

  val storage = new Storage {
    override def findFile(fileId: Long): Option[File] =
      OverviewDatabase.withSlickSession { implicit session =>
        Files.filter(f => f.id === fileId).firstOption
      }

    override def findPageSize(pageId: Long): Option[Long] =
      OverviewDatabase.withSlickSession { implicit session =>
       val query =  sql"SELECT octet_length(data) FROM page WHERE id = $pageId".as[Long]

        query.firstOption
      }

    override def largeObjectInputStream(oid: Long): InputStream = new PlayLargeObjectInputStream(oid)
    
    override def pageDataStream(pageId: Long): Option[InputStream] =
      OverviewDatabase.withSlickSession { implicit session =>
        val q = Pages.filter(_.id === pageId).map(_.data)
        q.firstOption.map(new ByteArrayInputStream(_))
      }

  }
}