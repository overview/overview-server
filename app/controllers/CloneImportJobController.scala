package controllers

import controllers.auth.{ Authorities, AuthorizedAction }
import models.CloneImportJob
import models.orm.stores.CloneImportJobStore

trait CloneImportJobController extends Controller {
  import Authorities._

  trait Storage {
    def insertJob(job: CloneImportJob) : Unit
  }

  def create(sourceDocumentSetId: Long) = AuthorizedAction.inTransaction(userViewingDocumentSet(sourceDocumentSetId)) { implicit request =>
    val job = CloneImportJob(ownerEmail=request.user.email, sourceDocumentSetId=sourceDocumentSetId)
    storage.insertJob(job)
    Redirect(routes.DocumentSetController.index()).flashing(
      "event" -> "document-set-create-clone"
    )
  }

  val storage : CloneImportJobController.Storage
}

object CloneImportJobController extends CloneImportJobController {
  object DatabaseStorage extends Storage {
    override def insertJob(job: CloneImportJob) = {
      CloneImportJobStore.insert(job)
    }
  }

  override val storage = DatabaseStorage
}
