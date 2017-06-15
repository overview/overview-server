package controllers

import javax.inject.Inject
import play.api.i18n.{MessagesApi,Messages}
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities._
import controllers.backend.{TagBackend,TreeBackend}
import controllers.forms.TreeCreationJobForm
import controllers.forms.TreeUpdateAttributesForm

class TreeController @Inject() (
  backend: TreeBackend,
  tagBackend: TagBackend,
  messagesApi: MessagesApi
) extends Controller(messagesApi) {
  /** A translated description of the tree (based on tag ID), or `""` if the
    * tag isn't specified or the specified tag doesn't exist.
    */
  private def treeDescription(documentSetId: Long, maybeTagId: Option[Long]): Future[String] = {
    maybeTagId match {
      case None => Future.successful("")
      case Some(tagId) => {
        tagBackend.show(documentSetId, tagId).map(_ match {
          case None => ""
          case Some(tag) => Messages("controllers.TreeController.treeDescription.fromTag", tag.name)
        })
      }
    }
  }

  def create(documentSetId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)).async { implicit request =>
    val form = TreeCreationJobForm(documentSetId)
    form.bindFromRequest.fold(
      f => Future.successful(BadRequest),
      attributes => {
        for {
          description <- treeDescription(documentSetId, attributes.tagId)
          tree <- backend.create(attributes.copy(description=description))
        } yield Created(views.json.Tree.show(tree))
      }
    )
  }

  def update(documentSetId: Long, treeId: Long) = AuthorizedAction(userOwningTree(treeId)).async { implicit request =>
    TreeUpdateAttributesForm().bindFromRequest.fold(
      f => Future.successful(BadRequest),
      attributes => backend.update(treeId, attributes).map(_ match {
        case Some(tree) => Ok(views.json.Tree.show(tree))
        case None => NotFound
      })
    )
  }

  def destroy(documentSetId: Long, treeId: Long) = AuthorizedAction(userOwningTree(treeId)).async { request =>
    for { unit <- backend.destroy(treeId) } yield NoContent
  }
}
