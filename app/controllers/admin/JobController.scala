package controllers.admin

import com.google.inject.ImplementedBy
import javax.inject.Inject
import play.api.i18n.MessagesApi
import play.api.libs.concurrent.Execution.Implicits._
import scala.concurrent.Future

import com.overviewdocs.database.HasDatabase
import com.overviewdocs.messages.DocumentSetCommands
import controllers.auth.Authorities.adminUser
import controllers.auth.AuthorizedAction
import controllers.backend.{CloneJobBackend,CsvImportBackend,FileGroupBackend,ImportJobBackend,TreeBackend}
import controllers.util.JobQueueSender
import controllers.Controller

class JobController @Inject() (
  cloneJobBackend: CloneJobBackend,
  csvImportBackend: CsvImportBackend,
  fileGroupBackend: FileGroupBackend,
  importJobBackend: ImportJobBackend,
  jobQueueSender: JobQueueSender,
  treeBackend: TreeBackend,
  storage: JobController.Storage,
  messagesApi: MessagesApi
) extends Controller(messagesApi) {

  def indexJson = AuthorizedAction(adminUser).async { implicit request =>
    for {
      jobs <- importJobBackend.indexWithDocumentSetsAndOwners
      trees <- treeBackend.indexIncompleteWithDocumentSetAndOwnerEmail
    } yield Ok(views.json.admin.Job.index(jobs, trees))
  }

  def index = AuthorizedAction(adminUser) { implicit request =>
    Ok(views.html.admin.Job.index(request.user))
  }

  def destroyCloneJob(documentSetId: Long, id: Int) = AuthorizedAction(adminUser).async { implicit request =>
    for {
      _ <- cloneJobBackend.cancel(documentSetId, id)
    } yield NoContent
  }

  def destroyCsvImport(documentSetId: Long, id: Long) = AuthorizedAction(adminUser).async { implicit request =>
    for {
      _ <- csvImportBackend.cancel(documentSetId, id)
    } yield NoContent
  }

  def destroyDocumentCloudImport(documentSetId: Long, id: Int) = AuthorizedAction(adminUser).async { implicit request =>
    for {
      _ <- storage.cancelDocumentCloudImport(id)
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

  def destroyTree(id: Long) = AuthorizedAction(adminUser).async { implicit request =>
    treeBackend.destroy(id).map(_ => NoContent)
  }
}

object JobController {
  @ImplementedBy(classOf[JobController.DatabaseStorage])
  trait Storage {
    def cancelDocumentCloudImport(id: Int): Future[Unit]
  }

  class DatabaseStorage @Inject() extends Storage with HasDatabase {
    import database.api._
    import com.overviewdocs.models.tables.DocumentCloudImports

    override def cancelDocumentCloudImport(id: Int) = database.runUnit(
      DocumentCloudImports.filter(_.id === id).map(_.cancelled).update(true)
    )
  }
}
