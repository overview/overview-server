package controllers.backend

import scala.concurrent.Future
import slick.jdbc.StaticQuery

import org.overviewproject.models.DocumentTag
import org.overviewproject.models.tables.DocumentTags

trait TagDocumentBackend extends Backend {
  /** Returns a mapping from tag ID to number of documents with that tag.
    *
    * @param documentSetId Document set ID.
    * @param documentIds Documents to check for tags.
    */
  def count(documentSetId: Long, documentIds: Seq[Long]): Future[Map[Long,Int]]

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

trait DbTagDocumentBackend extends TagDocumentBackend with DbBackend {
  import databaseApi._
  import org.overviewproject.database.Slick.SimpleArrayJdbcType
  implicit val longSeqMapper = new SimpleArrayJdbcType[Long]("int8")

  override def count(documentSetId: Long, documentIds: Seq[Long]) = {
    if (documentIds.nonEmpty) {
      database.run(sql"""
        WITH
        t AS (SELECT id FROM tag WHERE document_set_id = $documentSetId),
        d AS (SELECT UNNEST($documentIds) AS id)
        SELECT dt.tag_id, COUNT(*)
        FROM document_tag dt
        INNER JOIN t ON dt.tag_id = t.id
        INNER JOIN d ON dt.document_id = d.id
        GROUP BY dt.tag_id
      """.as[(Long,Int)])
        .map(_.toMap)(database.executionContext)
    } else {
      Future.successful(Map())
    }
  }

  override def createMany(tagId: Long, documentIds: Seq[Long]) = {
    if (documentIds.nonEmpty) {
      val idValues = documentIds.map(long => s"($long)")
      database.runUnit(sqlu"""
        WITH
        document_ids AS (
          SELECT document_id
          FROM (VALUES #${idValues.mkString(",")})
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
      """)
    } else {
      Future.successful(())
    }
  }

  override def destroyMany(tagId: Long, documentIds: Seq[Long]) = {
    if (documentIds.nonEmpty) {
      val idValues = documentIds.map(long => s"($long)")
      database.runUnit(sqlu"""
        DELETE FROM document_tag
        WHERE tag_id = #${tagId}
        AND document_id IN (VALUES #${idValues.mkString(",")})
      """)
    } else {
      Future.successful(())
    }
  }

  override def destroyAll(tagId: Long) = {
    database.delete(byTagIdCompiled(tagId))
  }

  lazy val byTagIdCompiled = Compiled { (tagId: Rep[Long]) =>
    DocumentTags
      .filter(_.tagId === tagId)
  }
}

object TagDocumentBackend extends DbTagDocumentBackend with org.overviewproject.database.DatabaseProvider
