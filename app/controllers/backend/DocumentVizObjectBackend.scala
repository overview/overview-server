package controllers.backend

import play.api.libs.json.JsObject
import scala.concurrent.Future

import org.overviewproject.models.DocumentVizObject
import org.overviewproject.models.tables.DocumentVizObjects

trait DocumentVizObjectBackend {
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
}

trait DbDocumentVizObjectBackend extends DocumentVizObjectBackend { self: DbBackend =>
  override def show(documentId: Long, vizObjectId: Long) = db { session =>
    DbDocumentVizObjectBackend.byIds(documentId: Long, vizObjectId: Long)(session)
  }

  override def create(documentId: Long, vizObjectId: Long, json: Option[JsObject]) = db { session =>
    val dvo = DocumentVizObject(documentId, vizObjectId, json)
    DbDocumentVizObjectBackend.insert(dvo)(session)
  }

  override def update(documentId: Long, vizObjectId: Long, json: Option[JsObject]) = db { session =>
    val count = DbDocumentVizObjectBackend.update(documentId, vizObjectId, json)(session)
    if (count > 0) Some(DocumentVizObject(documentId, vizObjectId, json)) else None
  }

  override def destroy(documentId: Long, vizObjectId: Long) = db { session =>
    DbDocumentVizObjectBackend.destroy(documentId, vizObjectId)(session)
  }
}

object DbDocumentVizObjectBackend {
  import org.overviewproject.database.Slick.simple._

  private lazy val byIdsCompiled = Compiled { (documentId: Column[Long], vizObjectId: Column[Long]) =>
    DocumentVizObjects
      .where(_.documentId === documentId)
      .where(_.vizObjectId === vizObjectId)
  }

  private lazy val attributesByIdsCompiled = Compiled { (documentId: Column[Long], vizObjectId: Column[Long]) =>
    DocumentVizObjects
      .where(_.documentId === documentId)
      .where(_.vizObjectId === vizObjectId)
      .map(_.json)
  }

  private lazy val insertDocumentVizObject = (DocumentVizObjects returning DocumentVizObjects).insertInvoker

  def byIds(documentId: Long, vizObjectId: Long)(session: Session) = {
    byIdsCompiled(documentId, vizObjectId).firstOption()(session)
  }

  def insert(documentVizObject: DocumentVizObject)(session: Session): DocumentVizObject = exceptions.wrap {
    (insertDocumentVizObject += documentVizObject)(session)
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
