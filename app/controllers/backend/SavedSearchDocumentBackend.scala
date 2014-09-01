package controllers.backend

import scala.concurrent.Future
import org.overviewproject.tree.orm.DocumentSearchResult
import org.overviewproject.models.DocumentInfo
import org.overviewproject.models.tables.{DocumentInfos,DocumentSearchResults}

trait SavedSearchDocumentBackend {
  def index(searchResultId: Long) : Future[Seq[DocumentInfo]]
}

trait DbSavedSearchDocumentBackend extends SavedSearchDocumentBackend { self: DbBackend =>
  override def index(searchResultId: Long) = db { session =>
    DbSavedSearchDocumentBackend.bySearchResultId(searchResultId)(session)
  }
}

object DbSavedSearchDocumentBackend {
  import org.overviewproject.database.Slick.simple._

  private lazy val bySearchResultIdCompiled = Compiled { (searchResultId: Column[Long]) =>
    DocumentInfos.where(_.id in (DocumentSearchResults.where(_.searchResultId === searchResultId).map(_.documentId)))
  }

  def bySearchResultId(searchResultId: Long)(session: Session) = {
    bySearchResultIdCompiled(searchResultId).list()(session)
  }
}

object SavedSearchDocumentBackend extends DbSavedSearchDocumentBackend with DbBackend
