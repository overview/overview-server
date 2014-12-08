package controllers.backend

import scala.concurrent.Future

import org.overviewproject.models.Page
import org.overviewproject.models.tables.Pages

trait PageBackend {
  /** Returns a single Page. */
  def show(id: Long): Future[Option[Page]]
}

trait DbPageBackend extends PageBackend { self: DbBackend =>
  override def show(id: Long) = db { session => DbPageBackend.show(session, id) }
}

object DbPageBackend {
  import org.overviewproject.database.Slick.simple._

  private lazy val byId = Compiled { (id: Column[Long]) => Pages.filter(_.id === id) }

  private def show(session: Session, id: Long): Option[Page] = byId(id).firstOption(session)
}

object PageBackend extends DbPageBackend with DbBackend
