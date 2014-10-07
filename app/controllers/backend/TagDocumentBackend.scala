package controllers.backend

import scala.concurrent.Future
import scala.slick.jdbc.StaticQuery

import org.overviewproject.models.DocumentTag
import org.overviewproject.models.tables.DocumentTags

trait TagDocumentBackend {
  /** Create many DocumentTag objects, one per documentId.
    *
    * The caller must ensure the tagId and documentIds belong to the same
    * document set.
    *
    * Duplicate objects will be ignored.
    */
  def createMany(tagId: Long, documentIds: Seq[Long]): Future[Unit]

  /** Destroy many DocumentTag objects, one per documentId.
    *
    * The caller must ensure the tagId and documentIds belong to the same
    * document set.
    */
  def destroyMany(tagId: Long, documentIds: Seq[Long]): Future[Unit]

  /** Destroy all DocumentTag objects belonging to the specified Tag.
    */
  def destroyAll(tagId: Long): Future[Unit]
}

trait DbTagDocumentBackend extends TagDocumentBackend { self: DbBackend =>
  override def createMany(tagId: Long, documentIds: Seq[Long]) = {
    if (documentIds.nonEmpty) {
      db { session =>
        DbTagDocumentBackend.insertMany(tagId, documentIds)(session)
      }
    } else {
      Future.successful(())
    }
  }

  override def destroyMany(tagId: Long, documentIds: Seq[Long]) = {
    if (documentIds.nonEmpty) {
      db { session =>
        DbTagDocumentBackend.deleteMany(tagId, documentIds)(session)
      }
    } else {
      Future.successful(())
    }
  }

  override def destroyAll(tagId: Long) = db { session =>
    DbTagDocumentBackend.deleteAll(tagId)(session)
  }
}

object DbTagDocumentBackend {
  import org.overviewproject.database.Slick.simple._

  def insertMany(tagId: Long, documentIds: Seq[Long])(session: Session): Unit = {
    val idValues = documentIds.map(long => s"($long)")
    val q = s"""
      WITH
      document_ids AS (
        SELECT document_id
        FROM (VALUES ${idValues.mkString(",")})
          AS t(document_id)
      ),
      to_insert AS (
        SELECT ${tagId} AS tag_id, document_id
        FROM document_ids
      )
      INSERT INTO document_tag (document_id, tag_id)
      SELECT document_id, tag_id FROM to_insert ti
      WHERE NOT EXISTS (
        SELECT 1
        FROM document_tag dt
        WHERE (dt.document_id, dt.tag_id) = (ti.document_id, ti.tag_id)
      )
    """

    StaticQuery.updateNA(q).execute(session)
  }

  def deleteMany(tagId: Long, documentIds: Seq[Long])(session: Session): Unit = {
    val idValues = documentIds.map(long => s"($long)")
    val q = s"""
      DELETE FROM document_tag
      WHERE tag_id = ${tagId}
      AND document_id IN (VALUES ${idValues.mkString(",")})
    """

    StaticQuery.updateNA(q).execute(session)
  }

  lazy val byTagIdCompiled = Compiled { (tagId: Column[Long]) =>
    DocumentTags
      .filter(_.tagId === tagId)
  }

  def deleteAll(tagId: Long)(session: Session): Unit = {
    byTagIdCompiled(tagId)
      .delete(session)
  }
}

object TagDocumentBackend extends DbBackend with DbTagDocumentBackend
