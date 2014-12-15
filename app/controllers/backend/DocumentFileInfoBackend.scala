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
import models.archive.TextViewInfo

trait DocumentFileInfoBackend {

  def indexDocumentViewInfos(documentSetId: Long): Future[Seq[DocumentViewInfo]]
  
}

trait DbDocumentFileInfoBackend extends DocumentFileInfoBackend { self: DbBackend =>
  import scala.slick.jdbc.StaticQuery.interpolation
  import scala.slick.jdbc.GetResult
  import org.overviewproject.database.Slick.simple._

  override def indexDocumentViewInfos(documentSetId: Long): Future[Seq[DocumentViewInfo]] = db { implicit session =>
    pageViewInfos(documentSetId) ++ fileViewInfos(documentSetId) ++ textViewInfos(documentSetId)
  }
  
    
  private def pageViewInfos(documentSetId: Long)(implicit session: Session) = {
  	val q = sql"""
      SELECT
        d.title,
        p.page_number,
        COALESCE(p.data_location, 'pagebytea:' || p.id),
        p.data_size
      FROM document d
      INNER JOIN page p ON d.page_id = p.id
      WHERE d.document_set_id = $documentSetId
    """.as[(String, Int, String, Long)]

    q.list.map(documentViewInfoFactory.fromPage)
  }
  
  private def fileViewInfos(documentSetId: Long)(implicit session: Session) = {
    val q = for {
        d <- Documents if (d.documentSetId === documentSetId) && d.pageId.isEmpty
        f <- Files if d.fileId === f.id
      } yield (d.title, f.viewOid, f.viewSize)

    val fileInfo = q.list.map(f => (f._1.getOrElse(""), f._2, f._3))
    fileInfo.map(documentViewInfoFactory.fromFile)
  }
  
  
  private def textViewInfos(documentSetId: Long)(implicit session: Session) = {

    val q = sql"""
      SELECT title, supplied_id, id, page_number, octet_length(text) FROM document
      WHERE ((document_set_id = $documentSetId) AND
             (file_id IS NULL) AND
             (text IS NOT NULL))""".as[(Option[String], Option[String], Long, Option[Int], Long)]
    
    q.list.map { info =>
      val infoWithValues = (info._1.getOrElse(""), info._2.getOrElse(""), info._3, info._4, info._5)
      documentViewInfoFactory.fromText(infoWithValues)
    }
  }
  
  protected val documentViewInfoFactory: DocumentViewInfoFactory
  
  protected trait DocumentViewInfoFactory {
    def fromPage(info: (String, Int, String, Long)): DocumentViewInfo
    def fromFile(info: (String, Long, Long)): DocumentViewInfo
    def fromText(info: (String, String, Long, Option[Int], Long)): DocumentViewInfo
    
  }
}


object DocumentFileInfoBackend extends DbDocumentFileInfoBackend with DbBackend {
  override protected val documentViewInfoFactory = new DocumentViewInfoFactory {
    def fromPage(info: (String, Int, String, Long)): DocumentViewInfo = (PageViewInfo.apply _).tupled(info)
    def fromFile(info: (String, Long, Long)): DocumentViewInfo = (FileViewInfo.apply _).tupled(info)
    def fromText(info: (String, String, Long, Option[Int], Long)): DocumentViewInfo = (TextViewInfo.apply _).tupled(info)
  } 
}

