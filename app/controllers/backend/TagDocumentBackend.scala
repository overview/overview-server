package controllers.backend

import scala.concurrent.Future
import scala.slick.jdbc.StaticQuery

import org.overviewproject.models.DocumentTag
import org.overviewproject.models.tables.DocumentTags

trait TagDocumentBackend {
  /** Returns a mapping from tag ID to number of documents with that tag.
    *
    * @param documentSetId Document set ID.
    * @param documentIds Documents to check for tags.
    */
  def count(documentSetId: Long, documentIds: Iterable[Long]): Future[Map[Long,Int]]

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
  override def count(documentSetId: Long, documentIds: Iterable[Long]) = {
    if (documentIds.nonEmpty) {
      db { session =>
        DbTagDocumentBackend.count(documentSetId, documentIds)(session)
      }
    } else {
      Future.successful(Map())
    }
  }

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
  import org.overviewproject.database.Slick.SimpleArrayJdbcType
  import org.overviewproject.database.Slick.simple._
  implicit val longSeqMapper = new SimpleArrayJdbcType[Long]("int8")

  lazy val countCompiled = {
    StaticQuery.query[(Long,Seq[Long]),(Long,Int)]("""
      WITH
      t AS (SELECT id FROM tag WHERE document_set_id = ?),
      d AS (SELECT UNNEST(?) AS id)
      SELECT dt.tag_id, COUNT(*)
      FROM document_tag dt
      INNER JOIN t ON dt.tag_id = t.id
      INNER JOIN d ON dt.document_id = d.id
      GROUP BY dt.tag_id
    """)
  }

  def count(documentSetId: Long, documentIds: Iterable[Long])(session: Session): Map[Long,Int] = {
    countCompiled(documentSetId, documentIds.toSeq).list(session).toMap
  }

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
