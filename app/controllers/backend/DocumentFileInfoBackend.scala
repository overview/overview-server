package controllers.backend

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future

import org.overviewproject.database.DatabaseProvider
import org.overviewproject.models.tables.Documents
import org.overviewproject.models.tables.Files
import models.archive.{DocumentViewInfo,FileViewInfo,PageViewInfo,TextViewInfo}

trait DocumentFileInfoBackend extends Backend {
  def indexDocumentViewInfos(documentSetId: Long): Future[Seq[DocumentViewInfo]]
}

trait DbDocumentFileInfoBackend extends DocumentFileInfoBackend with DbBackend {
  import databaseApi._

  override def indexDocumentViewInfos(documentSetId: Long): Future[Seq[DocumentViewInfo]] = {
    for {
      infos1 <- pageViewInfos(documentSetId)
      infos2 <- fileViewInfos(documentSetId)
      infos3 <- textViewInfos(documentSetId)
    } yield infos1 ++ infos2 ++ infos3
  }

  private def pageViewInfos(documentSetId: Long): Future[Seq[DocumentViewInfo]] = {
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

    database.run(q).map { seq =>
      seq.map((PageViewInfo.apply _).tupled)
    }
  }

  private def fileViewInfos(documentSetId: Long): Future[Seq[DocumentViewInfo]] = {
    val q = for {
      d <- Documents if (d.documentSetId === documentSetId) && d.pageId.isEmpty
      f <- Files if d.fileId === f.id
    } yield (d.title, f.viewLocation, f.viewSize)

    database.seq(q).map { seq =>
      seq.map(f => FileViewInfo(f._1.getOrElse(""), f._2, f._3))
    }
  }
  
  
  private def textViewInfos(documentSetId: Long): Future[Seq[DocumentViewInfo]] = {
    val q = sql"""
      SELECT title, supplied_id, id, page_number, octet_length(text) FROM document
      WHERE ((document_set_id = $documentSetId) AND
             (file_id IS NULL) AND
             (text IS NOT NULL))""".as[(Option[String], Option[String], Long, Option[Int], Long)]
    
    database.run(q).map { seq =>
      seq.map(f => TextViewInfo(f._1.getOrElse(""), f._2.getOrElse(""), f._3, f._4, f._5))
    }
  }
}

object DocumentFileInfoBackend extends DbDocumentFileInfoBackend with DatabaseProvider
