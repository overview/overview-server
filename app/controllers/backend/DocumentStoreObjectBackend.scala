package controllers.backend

import play.api.libs.json.{Json,JsObject}
import scala.concurrent.Future
import scala.slick.jdbc.StaticQuery

import models.Selection
import org.overviewproject.models.DocumentStoreObject
import org.overviewproject.models.tables.{DocumentStoreObjects,StoreObjects}

trait DocumentStoreObjectBackend extends Backend {
  /** Fetches a single DocumentStoreObject.
    *
    * Returns `None` if the DocumentStoreObject does not exist.
    */
  def show(documentId: Long, storeObjectId: Long): Future[Option[DocumentStoreObject]]

  /** Shows how many DocumentStoreObjects exist, counted by StoreObject.
    *
    * There are no zero counts.
    *
    * @param storeId Store ID.
    * @param selection Documents to count.
    */
  def countByObject(storeId: Long, selection: Selection): Future[Map[Long,Int]]

  /** Creates a DocumentStoreObject and returns it.
    *
    * Throws `Conflict` or `ParentMissing` when the write fails for sensible
    * reasons.
    */
  def create(documentId: Long, storeObjectId: Long, json: Option[JsObject]): Future[DocumentStoreObject]

  /** Creates several DocumentStoreObjects and returns them.
    *
    * Throws `ParentMissing` on invalid reference. Never throws `Conflict`:
    * any existing (documentId, storeObjectId) rows will be overwritten. If the
    * insert fails, nothing will be inserted.
    *
    * @param storeId skip StoreObjects that aren't in this Store
    * @param entries DocumentStoreObjects to create
    */
  def createMany(storeId: Long, entries: Seq[DocumentStoreObject]): Future[Seq[DocumentStoreObject]]

  /** Modifies a DocumentStoreObject and returns the modified version.
    *
    * Does nothing when the DocumentStoreObject does not exist.
    */
  def update(documentId: Long, storeObjectId: Long, json: Option[JsObject]): Future[Option[DocumentStoreObject]]

  /** Destroys a DocumentStoreObject.
    *
    * Does nothing when the DocumentStoreObject does not exist.
    */
  def destroy(documentId: Long, storeObjectId: Long): Future[Unit]

  /** Destroys several DocumentStoreObjects.
    *
    * @param storeId skip StoreObjects that aren't in this Store
    * @param entries (document ID, object ID) pairs
    */
  def destroyMany(storeId: Long, entries: Seq[(Long,Long)]): Future[Unit]
}

trait DbDocumentStoreObjectBackend extends DocumentStoreObjectBackend { self: DbBackend =>
  import org.overviewproject.database.Slick.simple._

  lazy val byIdsCompiled = Compiled { (documentId: Column[Long], storeObjectId: Column[Long]) =>
    DocumentStoreObjects
      .filter(_.documentId === documentId)
      .filter(_.storeObjectId === storeObjectId)
  }

  lazy val attributesByIdsCompiled = Compiled { (documentId: Column[Long], storeObjectId: Column[Long]) =>
    DocumentStoreObjects
      .filter(_.documentId === documentId)
      .filter(_.storeObjectId === storeObjectId)
      .map(_.json)
  }

  private lazy val countByObjectCompiled = Compiled { (storeId: Column[Long]) =>
    val storeObjectIds = StoreObjects
      .filter(_.storeId === storeId)
      .map(_.id)

    DocumentStoreObjects
      .filter(_.storeObjectId in storeObjectIds)
      .groupBy(_.storeObjectId)
      .map { case (storeObjectId, group) => (storeObjectId, group.length) }
  }

  private def countByObjectAndDocumentIds(storeId: Long, documentIds: Seq[Long]) = {
    val storeObjectIds = StoreObjects
      .filter(_.storeId === storeId)
      .map(_.id)

    DocumentStoreObjects
      .filter(_.storeObjectId in storeObjectIds)
      .filter(_.documentId inSet documentIds)
      .groupBy(_.storeObjectId)
      .map { case (storeObjectId, group) => (storeObjectId, group.length) }
  }

  lazy val insertInvoker = (DocumentStoreObjects returning DocumentStoreObjects).insertInvoker

  override def show(documentId: Long, storeObjectId: Long) = db { session =>
    byIdsCompiled(documentId, storeObjectId).firstOption(session)
  }

  override def countByObject(storeId: Long, selection: Selection) = {
    import scala.concurrent.ExecutionContext.Implicits._
    selection.getAllDocumentIds.flatMap { documentIds: Seq[Long] =>
      // this val query is of different type than the other
      val query = countByObjectAndDocumentIds(storeId, documentIds)
      db { session => query.list(session).toMap }
    }
  }

  override def create(documentId: Long, storeObjectId: Long, json: Option[JsObject]) = db { session =>
    val dso = DocumentStoreObject(documentId, storeObjectId, json)
    exceptions.wrap {
      insertInvoker.insert(dso)(session)
    }
  }

  implicit lazy val getDsoResult = new scala.slick.jdbc.GetResult[DocumentStoreObject] {
    override def apply(v1: scala.slick.jdbc.PositionedResult): DocumentStoreObject = {
      DocumentStoreObject(
        v1.nextLong,
        v1.nextLong,
        v1.nextStringOption.map(Json.parse(_).as[JsObject])
      )
    }
  }

  override def createMany(storeId: Long, entries: Seq[DocumentStoreObject]) = db { session =>
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
      val dsosAsSqlTuples: Seq[String] = entries
        .map((dso: DocumentStoreObject) => "(" + dso.documentId + "," + dso.storeObjectId + "," + dso.json.map(jsonToSql _).getOrElse("NULL") + ")")

      val q = s"""
        WITH request AS (
          SELECT *
          FROM (VALUES ${dsosAsSqlTuples.mkString(",")})
            AS t(document_id, store_object_id, json_text)
          WHERE document_id IN (
              SELECT id
              FROM document
              WHERE document_set_id IN (
                SELECT document_set_id
                FROM api_token
                WHERE token IN (SELECT api_token FROM store WHERE id = ?)
              )
            )
            AND store_object_id IN (SELECT id FROM store_object WHERE store_id = ?)
        ),
        deleted AS (
          DELETE FROM document_store_object
          WHERE (document_id, store_object_id) IN (SELECT document_id, store_object_id FROM request)
          RETURNING 1
        )
        INSERT INTO document_store_object (document_id, store_object_id, json_text)
        SELECT document_id, store_object_id, json_text FROM request
        WHERE (SELECT COUNT(*) FROM deleted) IS NOT NULL
        RETURNING document_id, store_object_id, json_text
      """

      val sq = StaticQuery.query[Tuple2[Long,Long],DocumentStoreObject](q)
      sq.apply(Tuple2(storeId, storeId)).list(session)
    }
  }

  override def update(documentId: Long, storeObjectId: Long, json: Option[JsObject]) = db { session =>
    val count = attributesByIdsCompiled(documentId, storeObjectId).update(json)(session)
    if (count > 0) Some(DocumentStoreObject(documentId, storeObjectId, json)) else None
  }

  override def destroy(documentId: Long, storeObjectId: Long) = db { session =>
    byIdsCompiled(documentId, storeObjectId).delete(session)
  }

  override def destroyMany(storeId: Long, entries: Seq[(Long,Long)]) = db { session =>
    val tuplesAsSql: Seq[String] = entries
      .map((t: Tuple2[Long,Long]) => "(" + t._1 + "," + t._2 + ")")

    val q = s"""
      DELETE FROM document_store_object
      WHERE (document_id, store_object_id) IN (VALUES ${tuplesAsSql.mkString(",")})
        AND store_object_id IN (SELECT id FROM store_object WHERE store_id = ?)
    """

    StaticQuery.update[Long](q).apply(storeId).execute(session)
  }
}

object DocumentStoreObjectBackend extends DbDocumentStoreObjectBackend with DbBackend
