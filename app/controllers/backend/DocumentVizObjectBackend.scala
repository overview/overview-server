package controllers.backend

import play.api.libs.json.{Json,JsObject}
import scala.concurrent.Future
import scala.slick.jdbc.StaticQuery

import org.overviewproject.models.DocumentVizObject
import org.overviewproject.models.tables.DocumentVizObjects

trait DocumentVizObjectBackend extends Backend {
  /** Fetches a single DocumentVizObject.
    *
    * Returns `None` if the DocumentVizObject does not exist.
    */
  def show(documentId: Long, vizObjectId: Long): Future[Option[DocumentVizObject]]

  /** Creates a DocumentVizObject and returns it.
    *
    * Throws `Conflict` or `ParentMissing` when the write fails for sensible
    * reasons.
    */
  def create(documentId: Long, vizObjectId: Long, json: Option[JsObject]): Future[DocumentVizObject]

  /** Creates several DocumentVizObjects and returns them.
    *
    * Throws `ParentMissing` on invalid reference. Never throws `Conflict`:
    * any existing (documentId, vizObjectId) rows will be overwritten. If the
    * insert fails, nothing will be inserted.
    *
    * @param vizId Viz id: skip VizObjects and Documents that don't belong
    * @param entries DocumentVizObjects to create
    */
  def createMany(vizId: Long, entries: Seq[DocumentVizObject]): Future[Seq[DocumentVizObject]]

  /** Modifies a DocumentVizObject and returns the modified version.
    *
    * Does nothing when the DocumentVizObject does not exist.
    */
  def update(documentId: Long, vizObjectId: Long, json: Option[JsObject]): Future[Option[DocumentVizObject]]

  /** Destroys a DocumentVizObject.
    *
    * Does nothing when the DocumentVizObject does not exist.
    */
  def destroy(documentId: Long, vizObjectId: Long): Future[Unit]

  /** Destroys several DocumentVizObjects.
    *
    * @param vizId Viz id: skip VizObjects and Documents that don't belong
    * @param entries (document ID, object ID) pairs
    */
  def destroyMany(vizId: Long, entries: Seq[(Long,Long)]): Future[Unit]
}

trait DbDocumentVizObjectBackend extends DocumentVizObjectBackend { self: DbBackend =>
  override def show(documentId: Long, vizObjectId: Long) = db { session =>
    DbDocumentVizObjectBackend.byIds(documentId, vizObjectId)(session)
  }

  override def create(documentId: Long, vizObjectId: Long, json: Option[JsObject]) = db { session =>
    val dvo = DocumentVizObject(documentId, vizObjectId, json)
    DbDocumentVizObjectBackend.insert(dvo)(session)
  }

  override def createMany(vizId: Long, entries: Seq[DocumentVizObject]) = db { session =>
    DbDocumentVizObjectBackend.insertAll(vizId, entries)(session)
  }

  override def update(documentId: Long, vizObjectId: Long, json: Option[JsObject]) = db { session =>
    val count = DbDocumentVizObjectBackend.update(documentId, vizObjectId, json)(session)
    if (count > 0) Some(DocumentVizObject(documentId, vizObjectId, json)) else None
  }

  override def destroy(documentId: Long, vizObjectId: Long) = db { session =>
    DbDocumentVizObjectBackend.destroy(documentId, vizObjectId)(session)
  }

  override def destroyMany(vizId: Long, entries: Seq[(Long,Long)]) = db { session =>
    DbDocumentVizObjectBackend.deleteAll(vizId, entries)(session)
  }
}

object DbDocumentVizObjectBackend {
  import org.overviewproject.database.Slick.simple._

  private lazy val byIdsCompiled = Compiled { (documentId: Column[Long], vizObjectId: Column[Long]) =>
    DocumentVizObjects
      .filter(_.documentId === documentId)
      .filter(_.vizObjectId === vizObjectId)
  }

  private lazy val attributesByIdsCompiled = Compiled { (documentId: Column[Long], vizObjectId: Column[Long]) =>
    DocumentVizObjects
      .filter(_.documentId === documentId)
      .filter(_.vizObjectId === vizObjectId)
      .map(_.json)
  }

  private lazy val insertDocumentVizObject = DocumentVizObjects.insertInvoker

  def byIds(documentId: Long, vizObjectId: Long)(session: Session) = {
    byIdsCompiled(documentId, vizObjectId).firstOption(session)
  }

  def insert(documentVizObject: DocumentVizObject)(session: Session): DocumentVizObject = {
    exceptions.wrap {
      (insertDocumentVizObject += documentVizObject)(session)
      documentVizObject
    }
  }

  private lazy val getDvoResult = new scala.slick.jdbc.GetResult[DocumentVizObject] {
    override def apply(v1: scala.slick.jdbc.PositionedResult): DocumentVizObject = {
      DocumentVizObject(
        v1.nextLong,
        v1.nextLong,
        v1.nextStringOption.map(Json.parse(_).as[JsObject])
      )
    }
  }

  def insertAll(vizId: Long, documentVizObjects: Seq[DocumentVizObject])(session: Session): Seq[DocumentVizObject] = {
    exceptions.wrap {
      /*
       * We run both DELETE and INSERT in one query, to save bandwidth and let
       * Postgres handle atomicity. (This doesn't avoid the race between DELETE
       * and INSERT, but it does make Postgres roll back on error.)
       *
       * Do not omit `WHERE (SELECT COUNT(*) FROM deleted) IS NOT NULL`: that
       * actually executes the DELETE.
       */
      def jsonToSql(json: JsObject) = s"'${json.toString.replaceAll("'", "''")}'"
      val dvosAsSqlTuples: Seq[String] = documentVizObjects
        .map((dvo: DocumentVizObject) => "(" + dvo.documentId + "," + dvo.vizObjectId + "," + dvo.json.map(jsonToSql _).getOrElse("NULL") + ")")

      val q = s"""
        WITH request AS (
          SELECT *
          FROM (VALUES ${dvosAsSqlTuples.mkString(",")})
            AS t(document_id, viz_object_id, json_text)
          WHERE document_id IN (SELECT id FROM document WHERE document_set_id = (SELECT document_set_id FROM viz WHERE id = $vizId))
            AND viz_object_id IN (SELECT id FROM viz_object WHERE viz_id = $vizId)
        ),
        deleted AS (
          DELETE FROM document_viz_object
          WHERE (document_id, viz_object_id) IN (SELECT document_id, viz_object_id FROM request)
          RETURNING 1
        )
        INSERT INTO document_viz_object (document_id, viz_object_id, json_text)
        SELECT document_id, viz_object_id, json_text FROM request
        WHERE (SELECT COUNT(*) FROM deleted) IS NOT NULL
        RETURNING document_id, viz_object_id, json_text
      """

      StaticQuery.queryNA[DocumentVizObject](q)(getDvoResult).list(session)
    }
  }

  def deleteAll(vizId: Long, entries: Seq[(Long,Long)])(session: Session): Unit = {
    exceptions.wrap {
      val tuplesAsSql: Seq[String] = entries
        .map((t: Tuple2[Long,Long]) => "(" + t._1 + "," + t._2 + ")")

      val q = s"""
        DELETE FROM document_viz_object
        WHERE (document_id, viz_object_id) IN (VALUES ${tuplesAsSql.mkString(",")})
          AND viz_object_id IN (SELECT id FROM viz_object WHERE viz_id = $vizId)
      """

      StaticQuery.updateNA(q).execute(session)
    }
  }

  def update(documentId: Long, vizObjectId: Long, json: Option[JsObject])(session: Session): Int = {
    attributesByIdsCompiled(documentId, vizObjectId)
      .update(json)(session)
  }

  def destroy(documentId: Long, vizObjectId: Long)(session: Session): Unit = {
    attributesByIdsCompiled(documentId, vizObjectId)
      .delete(session)
  }
}

object DocumentVizObjectBackend extends DbDocumentVizObjectBackend with DbBackend
