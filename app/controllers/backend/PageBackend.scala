package controllers.backend

import scala.concurrent.Future

import com.overviewdocs.models.Page
import com.overviewdocs.models.tables.Pages

trait PageBackend {
  /** Returns a single Page. */
  def show(id: Long): Future[Option[Page]]
}

trait DbPageBackend extends PageBackend with DbBackend {
  import database.api._

  override def show(id: Long) = database.option(byId(id))

  private lazy val byId = Compiled { (id: Rep[Long]) => Pages.filter(_.id === id) }
}

object PageBackend extends DbPageBackend
