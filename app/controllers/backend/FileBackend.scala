package controllers.backend

import scala.concurrent.Future

import org.overviewproject.models.File
import org.overviewproject.models.tables.Files

trait FileBackend {
  /** Returns a single File. */
  def show(id: Long): Future[Option[File]]
}

trait DbFileBackend extends FileBackend { self: DbBackend =>
  override def show(id: Long) = db { session => DbFileBackend.show(session, id) }
}

object DbFileBackend {
  import org.overviewproject.database.Slick.simple._

  private lazy val byId = Compiled { (id: Column[Long]) => Files.filter(_.id === id) }

  private def show(session: Session, id: Long): Option[File] = byId(id).firstOption(session)
}

object FileBackend extends DbFileBackend with DbBackend
