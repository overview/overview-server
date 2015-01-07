package controllers

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future

import controllers.auth.{AuthorizedAction,AuthorizedRequest}
import controllers.auth.Authorities.userOwningTag
import controllers.backend.{SelectionBackend,TagDocumentBackend}

trait TagDocumentController extends Controller {
  protected val selectionBackend: SelectionBackend
  protected val tagDocumentBackend: TagDocumentBackend

  private def getDocumentIds(documentSetId: Long, request: AuthorizedRequest[_]): Future[Seq[Long]] = {
    val sr = selectionRequest(documentSetId, request)

    for {
      selection <- selectionBackend.findOrCreate(request.user.email, sr)
      ids <- selection.getAllDocumentIds
    } yield ids
  }

  def createMany(documentSetId: Long, tagId: Long) = AuthorizedAction(userOwningTag(documentSetId, tagId)).async { implicit request =>
    for {
      ids <- getDocumentIds(documentSetId, request)
      _ <- tagDocumentBackend.createMany(tagId, ids)
    } yield Created
  }

  def destroyMany(documentSetId: Long, tagId: Long) = AuthorizedAction(userOwningTag(documentSetId, tagId)).async { implicit request =>
    for {
      ids <- getDocumentIds(documentSetId, request)
      _ <- tagDocumentBackend.destroyMany(tagId, ids)
    } yield NoContent
  }
}

object TagDocumentController extends TagDocumentController {
  override protected val selectionBackend = SelectionBackend
  override protected val tagDocumentBackend = TagDocumentBackend
}
