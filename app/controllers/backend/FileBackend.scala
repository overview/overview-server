package controllers.backend

import scala.concurrent.Future

import com.overviewdocs.models.File
import com.overviewdocs.models.tables.Files

trait FileBackend {
  /** Returns a single File. */
  def show(id: Long): Future[Option[File]]
}

trait DbFileBackend extends FileBackend with DbBackend {
  import database.api._

  override def show(id: Long) = database.option(byId(id))

  private lazy val byId = Compiled { (id: Rep[Long]) => Files.filter(_.id === id) }
}

object FileBackend extends DbFileBackend
