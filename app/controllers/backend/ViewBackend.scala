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

trait DbViewBackend extends ViewBackend with DbBackend {
  import databaseApi._

  override def index(documentSetId: Long) = database.seq(byDocumentSetIdCompiled(documentSetId))

  override def show(id: Long) = database.option(byIdCompiled(id))

  override def create(documentSetId: Long, attributes: View.CreateAttributes) = {
    database.run(inserter.+=((documentSetId, attributes)))
  }

  override def update(id: Long, attributes: View.UpdateAttributes) = {
    val q = attributesByIdCompiled(id).update(attributes.title)
      .andThen(byIdCompiled(id).result.headOption)
    database.run(q)
  }

  override def destroy(id: Long) = database.delete(byIdCompiled(id))

  private lazy val byDocumentSetIdCompiled = Compiled { (documentSetId: Rep[Long]) =>
    Views.filter(_.documentSetId === documentSetId)
  }

  private lazy val byIdCompiled = Compiled { (id: Rep[Long]) =>
    Views.filter(_.id === id)
  }

  private lazy val attributesByIdCompiled = Compiled { (id: Rep[Long]) =>
    for (v <- Views if v.id === id) yield (v.title)
  }

  protected val inserter = (Views.map((v) => (v.documentSetId, v.createAttributes)) returning Views)
}

object ViewBackend extends DbViewBackend with org.overviewproject.database.DatabaseProvider
