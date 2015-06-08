package controllers.backend

import java.util.UUID
import scala.concurrent.Future

import org.overviewproject.models.Plugin
import org.overviewproject.models.tables.Plugins

trait PluginBackend {
  /** Lists all Plugins, in alphabetical order. */
  def index: Future[Seq[Plugin]]

  /** Creates a Plugin. */
  def create(attributes: Plugin.CreateAttributes): Future[Plugin]

  /** Modifies a Plugin and returns the modified version.
    *
    * Returns None if the Plugin does not exist.
    *
    * If you do not run this in a transaction, there is a potential race. This
    * method runs an UPDATE and then a SELECT. See
    * https://github.com/slick/slick/issues/963
    */
  def update(id: UUID, attributes: Plugin.UpdateAttributes): Future[Option[Plugin]]

  /** Destroys a Plugin. */
  def destroy(id: UUID): Future[Unit]
}

trait DbPluginBackend extends PluginBackend with DbBackend {
  import databaseApi._

  override def index = database.seq(indexCompiled)

  override def create(attributes: Plugin.CreateAttributes) = database.run(inserter.+=(Plugin.build(attributes)))

  override def update(id: UUID, attributes: Plugin.UpdateAttributes) = {
    val row = (
      attributes.name,
      attributes.description,
      attributes.url,
      attributes.autocreate,
      attributes.autocreateOrder
    )

    database.run {
      updatePluginAttributes(id).update(row)
        .flatMap(_ match {
          case 0 => DBIO.successful(None)
          case _ => byIdCompiled(id).result.headOption
        })(database.executionContext)
    }
  }

  override def destroy(id: UUID) = database.delete(byIdCompiled(id))

  private lazy val byIdCompiled = Compiled { (id: Rep[UUID]) =>
    Plugins.filter(_.id === id)
  }

  private lazy val indexCompiled = Compiled { Plugins.sortBy(_.name) }

  protected lazy val inserter = (Plugins returning Plugins)

  private lazy val updatePluginAttributes = Compiled { (id: Rep[UUID]) =>
    for (p <- Plugins if p.id === id) yield (p.name, p.description, p.url, p.autocreate, p.autocreateOrder)
  }
}

object PluginBackend extends DbPluginBackend with org.overviewproject.database.DatabaseProvider
