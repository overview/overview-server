package controllers.backend

import scala.concurrent.Future

import models.pagination.{Page,PageRequest}
import org.overviewproject.tree.orm.DocumentSearchResult
import org.overviewproject.models.DocumentInfo
import org.overviewproject.models.tables.{DocumentInfos,DocumentSearchResults}

trait SavedSearchDocumentBackend {
  def index(searchResultId: Long) : Future[Page[DocumentInfo]]
}

trait DbSavedSearchDocumentBackend extends SavedSearchDocumentBackend { self: DbBackend =>
  override def index(searchResultId: Long) = {
    val pageRequest = PageRequest(0, 10000000)
    page(
      DbSavedSearchDocumentBackend.bySearchResultId(searchResultId, pageRequest.offset, pageRequest.limit),
      DbSavedSearchDocumentBackend.countBySearchResultId(searchResultId),
      pageRequest
    )
  }
}

object DbSavedSearchDocumentBackend {
  import org.overviewproject.database.Slick.simple._

  private def q(searchResultId: Column[Long]) = {
    DocumentInfos
      .filter(_.id in (
        DocumentSearchResults
          .filter(_.searchResultId === searchResultId)
          .map(_.documentId)
        )
      )
  }


  private lazy val _bySearchResultId = Compiled { (searchResultId: Column[Long], offset: ConstColumn[Long], limit: ConstColumn[Long]) =>
    q(searchResultId)
      .drop(offset)
      .take(limit)
  }

  private lazy val _countBySearchResultId = Compiled { (searchResultId: Column[Long]) =>
    q(searchResultId).length
  }

  def bySearchResultId(searchResultId: Long, offset: Int, limit: Int) = _bySearchResultId(searchResultId, offset, limit)
  def countBySearchResultId(searchResultId: Long) = _countBySearchResultId(searchResultId)
}

object SavedSearchDocumentBackend extends DbSavedSearchDocumentBackend with DbBackend
