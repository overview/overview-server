package controllers

import controllers.auth.{ Authorities, AuthorizedAction }
import models.CloneImportJob
import models.orm.stores.CloneImportJobStore
import org.overviewproject.tree.orm.DocumentSetCreationJob

trait CloneImportJobController extends Controller {
  import Authorities._

  def create(sourceDocumentSetId: Long) = AuthorizedAction.inTransaction(userViewingDocumentSet(sourceDocumentSetId)) { implicit request =>
    val job = CloneImportJob(ownerEmail=request.user.email, sourceDocumentSetId=sourceDocumentSetId)
    val creationJob = storage.insertJob(job)
    Redirect(routes.DocumentSetController.show(creationJob.documentSetId)).flashing(
      "event" -> "document-set-create-clone"
    )
  }

  val storage : CloneImportJobController.Storage
}

object CloneImportJobController extends CloneImportJobController {
  trait Storage {
    def insertJob(job: CloneImportJob): DocumentSetCreationJob
  }

  object DatabaseStorage extends Storage {
    override def insertJob(job: CloneImportJob) = {
      CloneImportJobStore.insert(job)
    }
  }

  override val storage = DatabaseStorage
}
