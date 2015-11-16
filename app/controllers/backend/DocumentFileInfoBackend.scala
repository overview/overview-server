package controllers.backend

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future

import com.overviewdocs.models.tables.Documents
import com.overviewdocs.models.tables.Files
import models.archive.{DocumentViewInfo,FileViewInfo,PageViewInfo,TextViewInfo}

trait DocumentFileInfoBackend extends Backend {
  def indexDocumentViewInfos(documentIds: Seq[Long]): Future[Seq[DocumentViewInfo]]
}

trait DbDocumentFileInfoBackend extends DocumentFileInfoBackend with DbBackend {
  import database.api._

  override def indexDocumentViewInfos(documentIds: Seq[Long]): Future[Seq[DocumentViewInfo]] = {
    for {
      infos1 <- pageViewInfos(documentIds)
      infos2 <- fileViewInfos(documentIds)
      infos3 <- textViewInfos(documentIds)
    } yield infos1 ++ infos2 ++ infos3
  }

  private def selectionSql(documentIds: Seq[Long]): String = {
    val idsAsSqlTuples: Seq[String] = documentIds.map((id: Long) => s"($id)")
    s"""
      selection AS (
        SELECT *
        FROM (VALUES ${idsAsSqlTuples.mkString(",")}) AS t(document_id)
      )
    """
  }

  private def pageViewInfos(documentIds: Seq[Long]): Future[Seq[DocumentViewInfo]] = {
  	val q = sql"""
  	  WITH #${selectionSql(documentIds)}
      SELECT
        d.title,
        p.page_number,
        p.data_location,
        p.data_size
      FROM selection
      INNER JOIN document d ON selection.document_id = d.id AND d.page_id IS NOT NULL
      INNER JOIN page p ON d.page_id = p.id
    """.as[(Option[String], Int, String, Long)]

    database.run(q).map { seq =>
      seq.map(p => PageViewInfo(p._1.getOrElse(""), p._2, p._3, p._4))
    }
  }

  private def fileViewInfos(documentIds: Seq[Long]): Future[Seq[DocumentViewInfo]] = {
    val q = sql"""
      WITH #${selectionSql(documentIds)}
      SELECT d.title, f.view_location, f.view_size
      FROM selection
      INNER JOIN document d ON selection.document_id = d.id AND d.file_id IS NOT NULL AND d.page_id IS NULL
      INNER JOIN file f ON d.file_id = f.id
    """.as[(Option[String], String, Long)]

    database.run(q).map { seq =>
      seq.map(f => FileViewInfo(f._1.getOrElse(""), f._2, f._3))
    }
  }

  private def textViewInfos(documentIds: Seq[Long]): Future[Seq[DocumentViewInfo]] = {
    val q = sql"""
      WITH #${selectionSql(documentIds)}
      SELECT d.title, d.supplied_id, d.id, d.page_number, octet_length(text)
      FROM selection
      INNER JOIN document d ON selection.document_id = d.id AND d.file_id IS NULL AND d.text IS NOT NULL
    """.as[(Option[String], Option[String], Long, Option[Int], Long)]

    database.run(q).map { seq =>
      seq.map(f => TextViewInfo(f._1.getOrElse(""), f._2.getOrElse(""), f._3, f._4, f._5))
    }
  }
}

object DocumentFileInfoBackend extends DbDocumentFileInfoBackend
