package controllers

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import scala.concurrent.Future

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.userOwningDocumentSet
import controllers.backend.SelectionBackend
import models.orm.finders.DocumentFinder
import models.pagination.Page
import models.{IdList,OverviewDatabase,SelectionLike}
import org.overviewproject.tree.orm.{Document=>OldDocument}

trait DocumentListController extends Controller {
  protected val selectionBackend: SelectionBackend
  protected val storage: DocumentListController.Storage
  private val MaxPageSize = 100

  def index(documentSetId: Long, pageSize: Int, page: Int)
            = AuthorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    val sr = selectionRequest(documentSetId, request)
    val pr = pageRequest(request, MaxPageSize)

    val selectionFuture: Future[SelectionLike] = pr.offset match {
      case 0 => selectionBackend.create(request.user.email, sr)
      case _ => selectionBackend.findOrCreate(request.user.email, sr)
    }

    for {
      selection <- selectionFuture
      ids <- selection.getDocumentIds(pr)
      documents <- storage.getDocuments(ids.items)
    } yield {
      val page = Page(documents, ids.pageInfo)
      Ok(views.json.DocumentList.show(page))
    }
  }
}

object DocumentListController extends DocumentListController {
  override val selectionBackend = SelectionBackend

  trait Storage {
    type Row = Tuple3[OldDocument,Seq[Long],Seq[Long]]

    /** Given a list of IDs, returns Documents, node IDs and tag IDs.
      *
      * The response documents are in the same order as the IDs.
      */
    def getDocuments(ids: Seq[Long]): Future[Seq[Row]]
  }

  object DbStorage extends Storage {
    override def getDocuments(ids: Seq[Long]) = Future {
      OverviewDatabase.inTransaction {
        def stringToIds(s: Option[String]): Seq[Long] = IdList.longs(s.getOrElse("")).ids

        val rows: Map[Long,Row] = DocumentFinder
          .byIds(ids)
          .withNodeIdsAndTagIdsAsLongStrings
          .map { (t: Tuple3[OldDocument,Option[String],Option[String]]) =>
            t._1.id -> (t._1, stringToIds(t._2), stringToIds(t._3))
          }
          .toMap

        ids.flatMap(rows.get(_))
      }
    }
  }

  override val storage = DbStorage
}
