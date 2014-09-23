package controllers.backend

import scala.concurrent.Future

import org.overviewproject.models.Viz
import org.overviewproject.models.tables.Vizs

trait VizBackend {
  /** Lists all Vizs for a DocumentSet.
    *
    * Returns an empty list if the DocumentSet does not exist.
    */
  def index(documentSetId: Long): Future[Seq[Viz]]

  /** Fetches a single Viz.
    *
    * Returns `None` if the Viz does not exist.
    */
  def show(vizId: Long): Future[Option[Viz]]

  /** Creates a Viz.
    *
    * Returns an error if the database write fails.
    */
  def create(documentSetId: Long, attributes: Viz.CreateAttributes): Future[Viz]

  /** Modifies a Viz, and returns the modified verison.
    *
    * Returns None if the Viz does not exist.
    *
    * If you do not run this in a transaction, there is a potential race. This
    * method runs an UPDATE and then a SELECT. See
    * https://github.com/slick/slick/issues/963
    */
  def update(id: Long, attributes: Viz.UpdateAttributes): Future[Option[Viz]]

  //def destroy(vizId: Long): Future[Unit]
}

trait DbVizBackend extends VizBackend { self: DbBackend =>
  override def index(documentSetId: Long) = db { session =>
    DbVizBackend.byDocumentSetId(documentSetId)(session)
  }

  override def show(id: Long) = db { session =>
    DbVizBackend.byId(id)(session)
  }

  override def create(documentSetId: Long, attributes: Viz.CreateAttributes) = db { implicit session =>
    val id = DbVizBackend.nextVizId(documentSetId)(session)
    val viz = Viz.build(id, documentSetId, attributes)
    DbVizBackend.insert(viz)(session)
  }

  override def update(id: Long, attributes: Viz.UpdateAttributes) = db { implicit session =>
    val count = DbVizBackend.update(id, attributes)(session)
    if (count > 0) DbVizBackend.byId(id)(session) else None
  }
}

object DbVizBackend {
  import org.overviewproject.database.Slick.simple._

  private lazy val byDocumentSetIdCompiled = Compiled { (documentSetId: Column[Long]) =>
    Vizs.filter(_.documentSetId === documentSetId)
  }

  private lazy val byIdCompiled = Compiled { (id: Column[Long]) =>
    Vizs.filter(_.id === id)
  }

  private lazy val previousIdCompiled = Compiled { (min: Column[Long], max: Column[Long]) =>
    Vizs
      .filter(_.id >= min)
      .filter(_.id < max)
      .map(_.id)
      .max
  }

  private lazy val attributesByIdCompiled = Compiled { (id: Column[Long]) =>
    for (v <- Vizs if v.id === id) yield (v.title, v.json)
  }

  def nextVizId(documentSetId: Long)(session: Session): Long = {
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

  lazy val insertViz = (Vizs returning Vizs).insertInvoker

  def insert(viz: Viz)(session: Session): Viz = {
    (insertViz += viz)(session)
  }

  def update(id: Long, attributes: Viz.UpdateAttributes)(session: Session): Int = {
    attributesByIdCompiled(id).update(attributes.title, attributes.json)(session)
  }
}

object VizBackend extends DbVizBackend with DbBackend
