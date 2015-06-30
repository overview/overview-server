package controllers

import play.api.libs.concurrent.Execution.Implicits.defaultContext

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.{anyUser,userOwningDocumentSet}
import controllers.backend.DocumentSetBackend

trait FileImportController extends Controller {
  val documentSetBackend: DocumentSetBackend

  def _new = AuthorizedAction(anyUser).async { implicit request =>
    for {
      count <- documentSetBackend.countByUserEmail(request.user.email)
    } yield Ok(views.html.FileImport._new(request.user, count))
  }

  def edit(documentSetId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    val documentSetFuture = documentSetBackend.show(documentSetId)
      .map(_.getOrElse(throw new Exception(s"DocumentSet $documentSetId disappeared!")))

    for {
      documentSet <- documentSetFuture
      count <- documentSetBackend.countByUserEmail(request.user.email)
    } yield Ok(views.html.FileImport.edit(request.user, documentSet, count))
  }
}

object FileImportController extends FileImportController {
  override val documentSetBackend = DocumentSetBackend
}
