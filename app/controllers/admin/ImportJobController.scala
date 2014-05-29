package controllers.admin

import play.api.mvc.Controller
import controllers.auth.AuthorizedAction
import controllers.auth.Authorities.adminUser
import models.orm.finders.DocumentSetCreationJobFinder
import models.orm.stores.DocumentSetStore
import org.overviewproject.tree.orm.DocumentSetCreationJob
import org.overviewproject.tree.orm.DocumentSet
import models.orm.User

trait ImportJobController extends Controller {

  trait Storage {
    def findAllDocumentSetCreationJobs: Iterable[(DocumentSetCreationJob, DocumentSet, User)]
  }

  val storage: Storage

  def index() = AuthorizedAction(adminUser) { implicit request =>
    val jobs = storage.findAllDocumentSetCreationJobs
    
    Ok(views.html.admin.ImportJob.index(request.user, jobs))
  }

  def delete(importJobId: Long) = AuthorizedAction(adminUser) { implicit request =>
    DocumentSetCreationJobFinder.byDocumentSetCreationJob(importJobId).headOption match {
      case Some(job) =>
        DocumentSetStore.deleteOrCancelJob(job.documentSetId)
        Redirect(routes.ImportJobController.index())
          .flashing("success" -> "The document set and the job that spawned it have been deleted.")
      case None =>
        Redirect(routes.ImportJobController.index())
          .flashing("warning" -> "Could not delete job: it does not exist. Has it completed?")
    }
  }
}

object ImportJobController extends ImportJobController {

  object DatabaseStorage extends Storage {
    override def findAllDocumentSetCreationJobs: Iterable[(DocumentSetCreationJob, DocumentSet, User)] =
      DocumentSetCreationJobFinder.all.withDocumentSetsAndOwners.toSeq
  }
  
  override val storage = DatabaseStorage
}