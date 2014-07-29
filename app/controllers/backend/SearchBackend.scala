package controllers.backend

import java.sql.Timestamp
import scala.concurrent.Future

import controllers.util.JobQueueSender
import models.OverviewDatabase
import org.overviewproject.jobs.models.Search
import org.overviewproject.models.tables.searchResults
import org.overviewproject.tree.orm.SearchResult // should be models.SearchResult

trait SearchBackend {
  def createSearch(search: Search) : Future[Unit] // FIXME make it a Future[SearchResult]
  def findSearchResults(documentSetId: Long) : Future[Seq[SearchResult]]
  def findSearchResult(documentSetId: Long, query: String) : Future[Option[SearchResult]]
}

trait DbSearchBackend extends SearchBackend { self: DbBackend =>
  val jobQueueSender: JobQueueSender

  override def createSearch(search: Search) = jobQueueSender.send(search)

  override def findSearchResults(documentSetId: Long) = db { session =>
    DbSearchBackend.byDocumentSetId(documentSetId)(session)
  }

  override def findSearchResult(documentSetId: Long, query: String) = db { session =>
    DbSearchBackend.byDocumentSetIdAndQuery(documentSetId, query)(session).headOption
  }
}

object DbSearchBackend {
  import org.overviewproject.database.Slick.simple._

  private lazy val byDocumentSetIdCompiled = Compiled { (documentSetId: Column[Long]) =>
    searchResults.where(_.documentSetId === documentSetId)
  }

  private lazy val byDocumentSetIdAndQueryCompiled = Compiled { (documentSetId: Column[Long], query: Column[String]) =>
    searchResults.where(_.documentSetId === documentSetId).where(_.query === query)
  }

  def byDocumentSetId(documentSetId: Long)(session: Session) = {
    byDocumentSetIdCompiled(documentSetId).list()(session)
  }

  def byDocumentSetIdAndQuery(documentSetId: Long, query: String)(session: Session) = {
    byDocumentSetIdAndQueryCompiled(documentSetId, query).list()(session)
  }
}

object SearchBackend extends DbSearchBackend with DbBackend {
  override val jobQueueSender = JobQueueSender
}
