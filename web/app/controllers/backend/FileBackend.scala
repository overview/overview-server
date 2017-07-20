package controllers.backend

import com.google.inject.ImplementedBy
import javax.inject.Inject
import scala.concurrent.Future

import com.overviewdocs.database.Database
import com.overviewdocs.models.File
import com.overviewdocs.models.tables.Files

@ImplementedBy(classOf[DbFileBackend])
trait FileBackend {
  /** Returns a single File. */
  def show(id: Long): Future[Option[File]]
}

class DbFileBackend @Inject() (
  val database: Database
) extends FileBackend with DbBackend {
  import database.api._

  override def show(id: Long) = database.option(byId(id))

  private lazy val byId = Compiled { (id: Rep[Long]) => Files.filter(_.id === id) }
}
