package controllers.backend

import com.google.inject.ImplementedBy
import javax.inject.Inject
import scala.collection.immutable
import scala.concurrent.Future

import com.overviewdocs.database.Database
import com.overviewdocs.models.{View,ViewDocumentDetailLink,ViewFilter}
import com.overviewdocs.models.tables.Views

@ImplementedBy(classOf[DbViewBackend])
trait ViewBackend {
  /** Lists all Views for a DocumentSet.
    *
    * Returns an empty list if the DocumentSet does not exist.
    */
  def index(documentSetId: Long): Future[immutable.Seq[View]]

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
    * There is a potential race. This method runs an UPDATE and then a SELECT.
    * See https://github.com/slick/slick/issues/963
    */
  def update(id: Long, attributes: View.UpdateAttributes): Future[Option[View]]

  /** Modifies a View, and returns the modified version.
    *
    * Returns None if the View does not exist.
    *
    * There is a potential race. This method runs an UPDATE and then a SELECT.
    * See https://github.com/slick/slick/issues/963
    */
  def updateViewFilter(id: Long, maybeViewFilter: Option[ViewFilter]): Future[Option[View]]

  /** Modifies a View, and returns the modified version.
    *
    * Returns None if the View does not exist.
    *
    * There is a potential race. This method runs an UPDATE and then a SELECT.
    * See https://github.com/slick/slick/issues/963
    */
  def updateDocumentDetailLink(id: Long, maybeDocumentDetailLink: Option[ViewDocumentDetailLink]): Future[Option[View]]

  /** Destroys a View.
    *
    * Callers should also destroy the accompanying ApiToken to secure the
    * document set.
    */
  def destroy(viewId: Long): Future[Unit]
}

class DbViewBackend @Inject() (
  val database: Database
) extends ViewBackend with DbBackend {
  import database.api._

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

  override def updateViewFilter(id: Long, maybeViewFilter: Option[ViewFilter]) = {
    val q = viewFilterByIdCompiled(id).update((maybeViewFilter.map(_.url), maybeViewFilter.map(_.json)))
      .andThen(byIdCompiled(id).result.headOption)
    database.run(q)
  }

  override def updateDocumentDetailLink(id: Long, maybeLink: Option[ViewDocumentDetailLink]) = {
    val q = documentDetailLinkByIdCompiled(id).update((
      maybeLink.map(_.url),
      maybeLink.map(_.title),
      maybeLink.map(_.text),
      maybeLink.map(_.iconClass)
    ))
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

  private lazy val viewFilterByIdCompiled = Compiled { (id: Rep[Long]) =>
    for (v <- Views if v.id === id) yield (v.maybeFilterUrl, v.maybeFilterJson)
  }

  private lazy val documentDetailLinkByIdCompiled = Compiled { (id: Rep[Long]) =>
    for (v <- Views if v.id === id) yield (
      v.maybeDocumentDetailLinkUrl,
      v.maybeDocumentDetailLinkTitle,
      v.maybeDocumentDetailLinkText,
      v.maybeDocumentDetailLinkIconClass
    )
  }

  protected val inserter = (Views.map((v) => (v.documentSetId, v.createAttributes)) returning Views)
}
