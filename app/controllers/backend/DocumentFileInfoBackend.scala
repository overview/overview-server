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
        COALESCE(p.data_location, 'pagebytea:' || p.id),
        p.data_size
      FROM document d
      INNER JOIN page p ON d.page_id = p.id
      WHERE EXISTS (SELECT 1 FROM selection WHERE document_id = d.id)
    """.as[(Option[String], Int, String, Long)]

    database.run(q).map { seq =>
      seq.map(p => PageViewInfo(p._1.getOrElse(""), p._2, p._3, p._4))
    }
  }

  private def fileViewInfos(documentIds: Seq[Long]): Future[Seq[DocumentViewInfo]] = {
    val q = sql"""
      WITH #${selectionSql(documentIds)}
      , ids AS (
        SELECT id AS document_id, file_id
        FROM document
        WHERE id IN (SELECT document_id FROM selection)
          AND file_id IS NOT NULL
          AND page_id IS NULL
      )
      SELECT
        (SELECT title FROM document WHERE id = ids.document_id),
        (SELECT view_location FROM file WHERE id = ids.file_id),
        (SELECT view_size FROM file WHERE id = ids.file_id)
      FROM ids
    """.as[(Option[String], String, Long)]

    database.run(q).map { seq =>
      seq.map(f => FileViewInfo(f._1.getOrElse(""), f._2, f._3))
    }
  }

  private def textViewInfos(documentIds: Seq[Long]): Future[Seq[DocumentViewInfo]] = {
    val q = sql"""
      WITH #${selectionSql(documentIds)}
      SELECT title, supplied_id, id, page_number, octet_length(text)
      FROM document
      WHERE EXISTS (SELECT 1 FROM selection WHERE document_id = document.id)
        AND file_id IS NULL
        AND text IS NOT NULL
    """.as[(Option[String], Option[String], Long, Option[Int], Long)]

    database.run(q).map { seq =>
      seq.map(f => TextViewInfo(f._1.getOrElse(""), f._2.getOrElse(""), f._3, f._4, f._5))
    }
  }
}

object DocumentFileInfoBackend extends DbDocumentFileInfoBackend
