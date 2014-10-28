package controllers.backend

import scala.concurrent.Future

import org.overviewproject.models.View
import org.overviewproject.models.tables.Views

trait ViewBackend {
  /** Lists all Views for a DocumentSet.
    *
    * Returns an empty list if the DocumentSet does not exist.
    */
  def index(documentSetId: Long): Future[Seq[View]]

  /** Fetches a single View.
    *
    * Returns `None` if the View does not exist.
    */
  def show(viewId: Long): Future[Option[View]]

  /** Creates a View.
    *
    * Returns an error if the database write fails.
    */
  def create(documentSetId: Long, attributes: View.CreateAttributes): Future[View]

  /** Modifies a View, and returns the modified verison.
    *
    * Returns None if the View does not exist.
    *
    * If you do not run this in a transaction, there is a potential race. This
    * method runs an UPDATE and then a SELECT. See
    * https://github.com/slick/slick/issues/963
    */
  def update(id: Long, attributes: View.UpdateAttributes): Future[Option[View]]

  /** Destroys a View.
    *
    * Callers should also destroy the accompanying ApiToken to secure the
    * document set.
    */
  def destroy(viewId: Long): Future[Unit]
}

trait DbViewBackend extends ViewBackend { self: DbBackend =>
  override def index(documentSetId: Long) = db { session =>
    DbViewBackend.byDocumentSetId(documentSetId)(session)
  }

  override def show(id: Long) = db { session =>
    DbViewBackend.byId(id)(session)
  }

  override def create(documentSetId: Long, attributes: View.CreateAttributes) = db { implicit session =>
    val id = DbViewBackend.nextViewId(documentSetId)(session)
    val view = View.build(id, documentSetId, attributes)
    DbViewBackend.insert(view)(session)
  }

  override def update(id: Long, attributes: View.UpdateAttributes) = db { implicit session =>
    val count = DbViewBackend.update(id, attributes)(session)
    if (count > 0) DbViewBackend.byId(id)(session) else None
  }

  override def destroy(id: Long) = db { implicit session =>
    DbViewBackend.destroy(id)(session)
  }
}

object DbViewBackend {
  import org.overviewproject.database.Slick.simple._

  private lazy val byDocumentSetIdCompiled = Compiled { (documentSetId: Column[Long]) =>
    Views.filter(_.documentSetId === documentSetId)
  }

  private lazy val byIdCompiled = Compiled { (id: Column[Long]) =>
    Views.filter(_.id === id)
  }

  private lazy val previousIdCompiled = Compiled { (min: Column[Long], max: Column[Long]) =>
    Views
      .filter(_.id >= min)
      .filter(_.id < max)
      .map(_.id)
      .max
  }

  private lazy val attributesByIdCompiled = Compiled { (id: Column[Long]) =>
    for (v <- Views if v.id === id) yield (v.title)
  }

  def nextViewId(documentSetId: Long)(session: Session): Long = {
    val minId = documentSetId << 32L
    val maxId = (documentSetId + 1L) << 32L
    val previousId = previousIdCompiled(minId, maxId).run(session)
    previousId.map(_ + 1L).getOrElse(minId)
  }

  def byDocumentSetId(documentSetId: Long)(session: Session) = {
    byDocumentSetIdCompiled(documentSetId).list(session)
  }

  def byId(id: Long)(session: Session) = {
    byIdCompiled(id).firstOption(session)
  }

  lazy val insertView = (Views returning Views).insertInvoker

  def insert(view: View)(session: Session): View = {
    (insertView += view)(session)
  }

  def update(id: Long, attributes: View.UpdateAttributes)(session: Session): Int = {
    attributesByIdCompiled(id).update(attributes.title)(session)
  }

  def destroy(id: Long)(session: Session): Unit = {
    byIdCompiled(id).delete(session)
  }
}

object ViewBackend extends DbViewBackend with DbBackend
