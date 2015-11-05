package controllers

import play.api.libs.concurrent.Execution.Implicits._
import play.api.i18n.Messages
import scala.concurrent.Future

import com.overviewdocs.database.HasBlockingDatabase
import com.overviewdocs.models.{DocumentSetCreationJob,Tag,Tree}
import com.overviewdocs.models.tables.{DocumentSetCreationJobs,Tags,Trees}
import controllers.auth.AuthorizedAction
import controllers.auth.Authorities._
import controllers.backend.TreeBackend
import controllers.forms.TreeCreationJobForm
import controllers.forms.TreeUpdateAttributesForm

trait TreeController extends Controller {
  protected val backend: TreeBackend
  protected val storage: TreeController.Storage

  private def tagToTreeDescription(tag: Tag) : String = {
    Messages("controllers.TreeController.treeDescription.fromTag", tag.name)
  }

  private def augmentJobWithDescription(job: DocumentSetCreationJob.CreateAttributes) : Either[String,DocumentSetCreationJob.CreateAttributes] = {
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

  def create(documentSetId: Long) = AuthorizedAction(userOwningDocumentSet(documentSetId)) { implicit request =>
    val form = TreeCreationJobForm(documentSetId)
    form.bindFromRequest.fold(
      f => BadRequest,
      j => {
        augmentJobWithDescription(j) match {
          case Right(goodJob) => {
            storage.insertJob(goodJob)
            NoContent
          }
          case Left(_) => NotFound
        }
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

object TreeController extends TreeController {
  trait Storage {
    def findTree(id: Long) : Option[Tree]
    def findTag(documentSetId: Long, tagId: Long) : Option[Tag]

    /** Inserts the job into the database and returns that copy */
    def insertJob(job: DocumentSetCreationJob.CreateAttributes): Unit
  }

  object DatabaseStorage extends Storage with HasBlockingDatabase {
    import database.api._

    override def findTree(id: Long) = blockingDatabase.option(Trees.filter(_.id === id))

    override def findTag(documentSetId: Long, tagId: Long) = {
      blockingDatabase.option(
        Tags
          .filter(_.id === tagId)
          .filter(_.documentSetId === documentSetId)
      )
    }

    override def insertJob(job: DocumentSetCreationJob.CreateAttributes) = {
      blockingDatabase.runUnit(DocumentSetCreationJobs.map(_.createAttributes).+=(job))
    }
  }

  override val backend = TreeBackend
  override val storage = DatabaseStorage
}
