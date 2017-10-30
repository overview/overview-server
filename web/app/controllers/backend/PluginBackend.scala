package controllers.backend

import com.google.inject.ImplementedBy
import java.util.UUID
import javax.inject.Inject
import scala.collection.immutable
import scala.concurrent.Future

import com.overviewdocs.database.Database
import com.overviewdocs.models.Plugin
import com.overviewdocs.models.tables.Plugins

@ImplementedBy(classOf[DbPluginBackend])
trait PluginBackend {
  /** Lists all Plugins, in alphabetical order. */
  def index: Future[immutable.Seq[Plugin]]

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

class DbPluginBackend @Inject() (val database: Database) extends PluginBackend with DbBackend {
  import database.api._
  import database.executionContext

  override def index = database.seq(indexCompiled)

  override def create(attributes: Plugin.CreateAttributes) = database.run(inserter.+=(Plugin.build(attributes)))

  override def update(id: UUID, attributes: Plugin.UpdateAttributes) = {
    val row = (
      attributes.name,
      attributes.description,
      attributes.url,
      attributes.serverUrlFromPlugin,
      attributes.autocreate,
      attributes.autocreateOrder
    )

    database.run {
      updatePluginAttributes(id).update(row)
        .flatMap(_ match {
          case 0 => DBIO.successful(None)
          case _ => byIdCompiled(id).result.headOption
        })
    }
  }

  override def destroy(id: UUID) = database.delete(byIdCompiled(id))

  private lazy val byIdCompiled = Compiled { (id: Rep[UUID]) =>
    Plugins.filter(_.id === id)
  }

  private lazy val indexCompiled = Compiled { Plugins.sortBy(_.name) }

  protected lazy val inserter = (Plugins returning Plugins)

  private lazy val updatePluginAttributes = Compiled { (id: Rep[UUID]) =>
    for (p <- Plugins if p.id === id) yield (p.name, p.description, p.url, p.serverUrlFromPlugin, p.autocreate, p.autocreateOrder)
  }
}
