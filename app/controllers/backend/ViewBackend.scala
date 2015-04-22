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
  import org.overviewproject.database.Slick.simple._

  override def index(documentSetId: Long) = db { session =>
    byDocumentSetIdCompiled(documentSetId).list(session)
  }

  override def show(id: Long) = db { session =>
    byId(id)(session)
  }

  override def create(documentSetId: Long, attributes: View.CreateAttributes) = {
    val i = inserter
    db { session => i.insert((documentSetId, attributes))(session) }
  }

  override def update(id: Long, attributes: View.UpdateAttributes) = db { session =>
    val count = attributesByIdCompiled(id).update(attributes.title)(session)
    if (count > 0) byId(id)(session) else None
  }

  override def destroy(id: Long) = db { implicit session =>
    byIdCompiled(id).delete(session)
  }

  private lazy val byDocumentSetIdCompiled = Compiled { (documentSetId: Column[Long]) =>
    Views.filter(_.documentSetId === documentSetId)
  }

  private lazy val byIdCompiled = Compiled { (id: Column[Long]) =>
    Views.filter(_.id === id)
  }

  private lazy val attributesByIdCompiled = Compiled { (id: Column[Long]) =>
    for (v <- Views if v.id === id) yield (v.title)
  }

  private def byId(id: Long)(session: Session) = {
    byIdCompiled(id).firstOption(session)
  }

  private val inserter = (Views.map((v) => (v.documentSetId, v.createAttributes)) returning Views).insertInvoker
}

object ViewBackend extends DbViewBackend with DbBackend
