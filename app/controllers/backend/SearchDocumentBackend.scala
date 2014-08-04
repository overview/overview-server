package controllers.backend

import scala.concurrent.Future
import org.overviewproject.tree.orm.{Document,DocumentSearchResult} // should be models.Document
import org.overviewproject.models.tables.{Documents,DocumentSearchResults}

trait SearchDocumentBackend {
  def index(searchResultId: Long) : Future[Seq[Document]]
}

trait DbSearchDocumentBackend extends SearchDocumentBackend { self: DbBackend =>
  override def index(searchResultId: Long) = db { session =>
    DbSearchDocumentBackend.bySearchResultId(searchResultId)(session)
  }
}

object DbSearchDocumentBackend {
  import org.overviewproject.database.Slick.simple._

  private lazy val bySearchResultIdCompiled = Compiled { (searchResultId: Column[Long]) =>
    Documents.where(_.id in (DocumentSearchResults.where(_.searchResultId === searchResultId).map(_.documentId)))
  }

  def bySearchResultId(searchResultId: Long)(session: Session) = {
    bySearchResultIdCompiled(searchResultId).list()(session)
  }
}

object SearchDocumentBackend extends DbSearchDocumentBackend with DbBackend
