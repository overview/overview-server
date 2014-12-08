package controllers.backend

import scala.concurrent.Future

import org.overviewproject.models.File
import org.overviewproject.models.tables.Files

trait FileBackend {
  /** Returns a single File. */
  def show(id: Long): Future[Option[File]]
}

trait DbFileBackend extends FileBackend { self: DbBackend =>
  override def show(id: Long) = ???
}

object FileBackend extends DbFileBackend with DbBackend
