package controllers.backend

import java.sql.Timestamp
import scala.concurrent.Future

import controllers.util.JobQueueSender
import org.overviewproject.jobs.models.Search
import org.overviewproject.models.tables.SearchResults
import org.overviewproject.tree.orm.SearchResult // should be models.SearchResult

trait SearchBackend {
  def create(search: Search) : Future[Unit] // FIXME make it a Future[SearchResult]
  def index(documentSetId: Long) : Future[Seq[SearchResult]]
  def show(documentSetId: Long, query: String) : Future[Option[SearchResult]]
  def destroy(documentSetId: Long, query: String) : Future[Unit]
}

trait DbSearchBackend extends SearchBackend { self: DbBackend =>
  val jobQueueSender: JobQueueSender

  override def create(search: Search) = jobQueueSender.send(search)

  override def index(documentSetId: Long) = db { session =>
    DbSearchBackend.byDocumentSetId(documentSetId)(session)
  }

  override def show(documentSetId: Long, query: String) = db { session =>
    DbSearchBackend.byDocumentSetIdAndQuery(documentSetId, query)(session).headOption
  }

  override def destroy(documentSetId: Long, query: String) = db { session =>
    DbSearchBackend.deleteByDocumentSetIdAndQuery(documentSetId, query)(session)
  }
}

object DbSearchBackend {
  import org.overviewproject.database.Slick.simple._

  private lazy val byDocumentSetIdCompiled = Compiled { (documentSetId: Column[Long]) =>
    SearchResults.where(_.documentSetId === documentSetId)
  }

  private lazy val byDocumentSetIdAndQueryCompiled = Compiled { (documentSetId: Column[Long], query: Column[String]) =>
    SearchResults.where(_.documentSetId === documentSetId).where(_.query === query)
  }

  def byDocumentSetId(documentSetId: Long)(session: Session) = {
    byDocumentSetIdCompiled(documentSetId).list()(session)
  }

  def byDocumentSetIdAndQuery(documentSetId: Long, query: String)(session: Session) = {
    byDocumentSetIdAndQueryCompiled(documentSetId, query).list()(session)
  }

  def deleteByDocumentSetIdAndQuery(documentSetId: Long, query: String)(session: Session) = {
    byDocumentSetIdAndQueryCompiled(documentSetId, query).delete(session)
  }
}

object SearchBackend extends DbSearchBackend with DbBackend {
  override val jobQueueSender = JobQueueSender
}
