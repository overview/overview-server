package controllers.backend

import scala.concurrent.Future
import models.DocumentFileInfo
import org.overviewproject.models.tables.Documents
import org.overviewproject.models.tables.Files
import models.archive.PageViewInfo
import models.archive.FileViewInfo

trait DocumentFileInfoBackend {

  def indexDocumentFileInfosForPages(documentSetId: Long): Future[Seq[PageViewInfo]]
  def indexDocumentFileInfosForFiles(documentSetId: Long): Future[Seq[FileViewInfo]]
}

trait DbDocumentFileInfoBackend extends DocumentFileInfoBackend { self: DbBackend =>
  import scala.slick.jdbc.StaticQuery.interpolation
  import scala.slick.jdbc.GetResult
  import org.overviewproject.database.Slick.simple._

  override def indexDocumentFileInfosForPages(documentSetId: Long): Future[Seq[PageViewInfo]] = db { implicit session =>
  	val q = sql"""
        SELECT d.title, p.page_number, d.page_id, octet_length(p.data) FROM document d, page p 
        WHERE ((d.document_set_id = $documentSetId) AND 
               (d.page_id IS NOT NULL) AND 
               (p.id = d.page_id))""".as[(String, Int, Long, Long)]

    q.list.map(documentViewInfoFactory.fromPage)
  }
  
  override def indexDocumentFileInfosForFiles(documentSetId: Long): Future[Seq[FileViewInfo]] = db { implicit session =>
    val q = for {
        d <- Documents if (d.documentSetId === documentSetId) && d.pageId.isEmpty
        f <- Files if d.fileId === f.id
      } yield (d.title, f.viewOid, f.viewSize)

    q.list.map{f => FileViewInfo(f._1.getOrElse(""), f._2, f._3.getOrElse(0))}
  }
    
  protected val documentViewInfoFactory: DocumentViewInfoFactory
  
  protected trait DocumentViewInfoFactory {
    def fromPage(info: (String, Int, Long, Long)): PageViewInfo
    def fromFile(title: String, viewOid: Long, size: Long): FileViewInfo
  }
}


object DocumentFileInfoBackend extends DbDocumentFileInfoBackend with DbBackend {
  override protected val documentViewInfoFactory = new DocumentViewInfoFactory {
    def fromPage(info: (String, Int, Long, Long)): PageViewInfo = (PageViewInfo.apply _).tupled(info)
    
    def fromFile(title: String, viewOid: Long, size: Long): FileViewInfo = ???
  } 
}

