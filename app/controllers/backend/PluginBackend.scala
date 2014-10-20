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

trait DbPluginBackend extends PluginBackend { self: DbBackend =>
  import org.overviewproject.database.Slick.simple._

  private lazy val selectPlugin = Compiled { (id: Column[UUID]) =>
    Plugins.filter(_.id === id)
  }

  override def index = db { session =>
    Plugins
      .sortBy(_.name)
      .list(session)
  }

  private lazy val insertPlugin = (Plugins returning Plugins).insertInvoker
  override def create(attributes: Plugin.CreateAttributes) = {
    val q = insertPlugin // avoid escaping defining scope
    db { session =>
      q.insert(Plugin.build(attributes))(session)
    }
  }

  private lazy val updatePluginAttributes = Compiled { (id: Column[UUID]) =>
    for (p <- Plugins if p.id === id) yield (p.name, p.description, p.url)
  }
  override def update(id: UUID, attributes: Plugin.UpdateAttributes) = {
    val s = selectPlugin(id) // avoid escaping defining scope
    val u = updatePluginAttributes(id) // avoid escaping defining scope
    db { session =>
      val count = u.update(attributes.name, attributes.description, attributes.url)(session)
      if (count > 0) s.firstOption(session) else None
    }
  }

  override def destroy(id: UUID) = {
    val q = selectPlugin(id)
    db { session =>
      q.delete(session)
    }
  }
}

object PluginBackend extends DbPluginBackend with DbBackend
