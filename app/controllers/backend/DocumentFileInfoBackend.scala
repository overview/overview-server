package controllers.backend

import scala.concurrent.Future
import models.DocumentFileInfo
import org.overviewproject.models.tables.Documents
import org.overviewproject.models.tables.Files
import models.archive.PageViewInfo
import models.archive.FileViewInfo
import models.archive.DocumentViewInfo
import models.archive.PageViewInfo
import models.archive.FileViewInfo

trait DocumentFileInfoBackend {

  def indexDocumentViewInfos(documentSetId: Long): Future[Seq[DocumentViewInfo]]
  
}

trait DbDocumentFileInfoBackend extends DocumentFileInfoBackend { self: DbBackend =>
  import scala.slick.jdbc.StaticQuery.interpolation
  import scala.slick.jdbc.GetResult
  import org.overviewproject.database.Slick.simple._

  override def indexDocumentViewInfos(documentSetId: Long): Future[Seq[DocumentViewInfo]] = db { implicit session =>
    pageViewInfos(documentSetId) ++ fileViewInfos(documentSetId)
  }
  
    
  private def pageViewInfos(documentSetId: Long)(implicit session: Session) = {
  	val q = sql"""
        SELECT d.title, p.page_number, d.page_id, octet_length(p.data) FROM document d, page p 
        WHERE ((d.document_set_id = $documentSetId) AND 
               (d.page_id IS NOT NULL) AND 
               (p.id = d.page_id))""".as[(String, Int, Long, Long)]

    q.list.map(documentViewInfoFactory.fromPage1)
  }
  
  private def fileViewInfos(documentSetId: Long)(implicit session: Session) = {
    val q = for {
        d <- Documents if (d.documentSetId === documentSetId) && d.pageId.isEmpty
        f <- Files if d.fileId === f.id
      } yield (d.title, f.viewOid, f.viewSize)

    val fileInfo = q.list.map(f => (f._1.getOrElse(""), f._2, f._3.getOrElse(0l)))
    fileInfo.map(documentViewInfoFactory.fromFile1)
  }
  
  protected val documentViewInfoFactory: DocumentViewInfoFactory
  
  protected trait DocumentViewInfoFactory {
    def fromPage1(info: (String, Int, Long, Long)): DocumentViewInfo
    def fromFile1(info: (String, Long, Long)): DocumentViewInfo
    
  }
}


object DocumentFileInfoBackend extends DbDocumentFileInfoBackend with DbBackend {
  override protected val documentViewInfoFactory = new DocumentViewInfoFactory {
    def fromPage1(info: (String, Int, Long, Long)): DocumentViewInfo = (PageViewInfo.apply _).tupled(info)
    def fromFile1(info: (String, Long, Long)): DocumentViewInfo = (FileViewInfo.apply _).tupled(info)
    
  } 
}

