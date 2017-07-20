package controllers.backend

import com.google.inject.ImplementedBy
import javax.inject.Inject
import scala.concurrent.Future

import com.overviewdocs.database.Database
import com.overviewdocs.models.Page
import com.overviewdocs.models.tables.Pages

@ImplementedBy(classOf[DbPageBackend])
trait PageBackend {
  /** Returns a single Page. */
  def show(id: Long): Future[Option[Page]]
}

class DbPageBackend @Inject() (val database: Database) extends PageBackend with DbBackend {
  import database.api._

  override def show(id: Long) = database.option(byId(id))

  private lazy val byId = Compiled { (id: Rep[Long]) => Pages.filter(_.id === id) }
}
