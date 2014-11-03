package controllers.backend

import scala.concurrent.Future
import models.DocumentFileInfo

case class PageViewInfo(documentTitle: String, pageNumber: Int, pageId: Long, size: Long)

trait DocumentFileInfoBackend {

  def indexDocumentFileInfosForPages(documentSetId: Long): Future[Seq[PageViewInfo]]
}

trait DbDocumentFileInfoBackend extends DocumentFileInfoBackend { self: DbBackend =>
  import scala.slick.jdbc.StaticQuery.interpolation
  import scala.slick.jdbc.GetResult
  import org.overviewproject.database.Slick.simple._

  override def indexDocumentFileInfosForPages(documentSetId: Long): Future[Seq[PageViewInfo]] = db { implicit session =>
  	implicit val getPageViewResult = GetResult(r => PageViewInfo(r.nextString, r.nextInt, r.nextLong, r.nextLong)) 

  	val q = sql"""
        SELECT d.title, p.page_number, d.page_id, octet_length(p.data) FROM document d, page p 
        WHERE ((d.document_set_id = $documentSetId) AND 
               (d.page_id IS NOT NULL) AND 
               (p.id = d.page_id))""".as[PageViewInfo]

    q.list
  }
}


