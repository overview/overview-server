package controllers

import play.api.libs.concurrent.Execution.Implicits._
import play.api.i18n.Messages

import controllers.auth.AuthorizedAction
import controllers.auth.Authorities._
import controllers.backend.TreeBackend
import controllers.forms.TreeCreationJobForm
import models.orm.finders.{TagFinder,TreeFinder}
import models.orm.stores.DocumentSetCreationJobStore
import org.overviewproject.tree.orm.{DocumentSetCreationJob,Tag,Tree}

trait TreeController extends Controller {
  protected val backend: TreeBackend
  protected val storage: TreeController.Storage

  private def tagToTreeDescription(tag: Tag) : String = {
    Messages("controllers.TreeController.treeDescription.fromTag", tag.name)
  }

  private def augmentJobWithDescription(job: DocumentSetCreationJob) : Either[String,DocumentSetCreationJob] = {
    job.tagId match {
      case None => Right(job)
      case Some(tagId) => {
        storage.findTag(job.documentSetId, tagId) match {
          case None => Left("tag not found")
          case Some(tag) => Right(job.copy(
            treeDescription=Some(tagToTreeDescription(tag))
          ))
        }
      }
    }
  }

  def create(documentSetId: Long) = AuthorizedAction.inTransaction(userOwningDocumentSet(documentSetId)) { implicit request =>
    val form = TreeCreationJobForm(documentSetId)
    form.bindFromRequest.fold(
      f => BadRequest,
      j => {
        augmentJobWithDescription(j) match {
          case Right(goodJob) =>
            storage.insertJob(goodJob)
            NoContent
          case Left(_) =>
            NotFound
        }
      }
    )
  }

  def destroy(documentSetId: Long, treeId: Long) = AuthorizedAction(userOwningTree(treeId)).async { implicit request =>
    for { unit <- backend.destroy(treeId) } yield NoContent
  }
}

object TreeController extends TreeController {
  trait Storage {
    def findTree(id: Long) : Option[Tree]
    def findTag(documentSetId: Long, tagId: Long) : Option[Tag]

    /** Inserts the job into the database and returns that copy */
    def insertJob(job: DocumentSetCreationJob): DocumentSetCreationJob
  }

  object DatabaseStorage extends Storage {
    override def findTree(id: Long) = TreeFinder.byId(id).headOption
    override def findTag(documentSetId: Long, tagId: Long) = TagFinder.byDocumentSetAndId(documentSetId, tagId).headOption
    override def insertJob(job: DocumentSetCreationJob) = DocumentSetCreationJobStore.insertOrUpdate(job)
  }

  override val backend = TreeBackend
  override val storage = DatabaseStorage
}
