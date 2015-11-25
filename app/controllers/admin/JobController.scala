package controllers.admin

import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future

import com.overviewdocs.messages.DocumentSetCommands
import controllers.auth.Authorities.adminUser
import controllers.auth.AuthorizedAction
import controllers.backend.{CsvImportBackend,FileGroupBackend,ImportJobBackend,TreeBackend}
import controllers.util.JobQueueSender
import controllers.Controller

trait JobController extends Controller {
  protected val csvImportBackend: CsvImportBackend
  protected val fileGroupBackend: FileGroupBackend
  protected val importJobBackend: ImportJobBackend
  protected val jobQueueSender: JobQueueSender
  protected val treeBackend: TreeBackend

  def indexJson = AuthorizedAction(adminUser).async { implicit request =>
    for {
      jobs <- importJobBackend.indexWithDocumentSetsAndOwners
      trees <- treeBackend.indexIncompleteWithDocumentSetAndOwnerEmail
    } yield Ok(views.json.admin.Job.index(jobs, trees))
  }

  def index = AuthorizedAction(adminUser) { implicit request =>
    Ok(views.html.admin.Job.index(request.user))
  }

  def destroyCsvImport(documentSetId: Long, id: Long) = AuthorizedAction(adminUser).async { implicit request =>
    for {
      _ <- csvImportBackend.cancel(documentSetId, id)
    } yield NoContent
  }

  def destroyFileGroup(documentSetId: Long, id: Long) = AuthorizedAction(adminUser).async { implicit request =>
    for {
      _ <- fileGroupBackend.destroy(id)
    } yield {
      jobQueueSender.send(DocumentSetCommands.CancelAddDocumentsFromFileGroup(documentSetId, id))
      NoContent
    }
  }

  def destroyDscj(id: Long) = AuthorizedAction(adminUser) { implicit request =>
    ???
  }

  def destroyTree(id: Long) = AuthorizedAction(adminUser).async { implicit request =>
    treeBackend.destroy(id).map(_ => NoContent)
  }
}

object JobController extends JobController {
  override protected val csvImportBackend = CsvImportBackend
  override protected val fileGroupBackend = FileGroupBackend
  override protected val importJobBackend = ImportJobBackend
  override protected val jobQueueSender = JobQueueSender
  override protected val treeBackend = TreeBackend
}
